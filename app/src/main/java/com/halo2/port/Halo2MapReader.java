package com.halo2.port;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Defensive reader for the known Halo 2 Xbox map header.
 *
 * This deliberately does not guess the tag-index record layout. It verifies
 * file bounds and emits a bounded raw index sample for format research.
 */
public final class Halo2MapReader {
    private static final long HEADER_MAGIC = 0x68656164L; // "head"
    private static final long FOOTER_MAGIC = 0x666F6F74L; // "foot"
    private static final long SUPPORTED_VERSION = 8L;
    private static final int HEADER_BYTES = 0x800;
    private static final int INDEX_SAMPLE_BYTES = 128;

    private Halo2MapReader() {}

    public static String inspect(ContentResolver resolver, Uri uri, long reportedLength)
            throws IOException {
        if (reportedLength < HEADER_BYTES) {
            return "MAP INSPECTION FAILED: file is shorter than the 64-byte header.";
        }

        byte[] header = readAt(resolver, uri, 0, HEADER_BYTES);
        ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        long magic = u32(h, 0x00);
        long version = u32(h, 0x04);
        long declaredLength = u32(h, 0x08);
        long indexOffset = u32(h, 0x10);
        long indexLength = u32(h, 0x14);
        long rawLength = u32(h, 0x18);
        long allocationLength = u32(h, 0x1C);

        StringBuilder out = new StringBuilder();
        out.append("HALO 2 MAP STRUCTURE CHECK\n");
        out.append("Signature: ").append(hex(magic));
        out.append(magic == HEADER_MAGIC ? " (head)\n" : " (unexpected)\n");
        out.append("Version: ").append(version).append('\n');
        out.append("Declared file length: ").append(declaredLength).append(" bytes\n");
        out.append("Actual file length: ").append(reportedLength).append(" bytes\n");
        out.append("Index offset: ").append(hex(indexOffset)).append(" (").append(indexOffset).append(")\n");
        out.append("Index length: ").append(hex(indexLength)).append(" (").append(indexLength).append(")\n");
        out.append("Raw-data length field: ").append(hex(rawLength)).append('\n');
        out.append("Allocation-length field: ").append(hex(allocationLength)).append('\n');

        boolean signatureOk = magic == HEADER_MAGIC;
        boolean versionOk = version == SUPPORTED_VERSION;
        boolean declaredLengthOk = declaredLength == reportedLength;
        boolean indexRangeOk = indexOffset <= reportedLength
                && indexLength <= reportedLength - indexOffset;

        out.append("\nVALIDATION\n");
        out.append("Header signature: ").append(pass(signatureOk)).append('\n');
        out.append("Supported version 8: ").append(pass(versionOk)).append('\n');
        out.append("Declared file length: ").append(pass(declaredLengthOk)).append('\n');
        out.append("Index range inside file: ").append(pass(indexRangeOk)).append('\n');

        // Halo 2 cache headers are 0x800 bytes; the footer signature is at 0x7FC.
        long footer = u32(h, 0x7FC);
        out.append("Header footer signature: ").append(hex(footer));
        out.append(footer == FOOTER_MAGIC ? " (foot)\n" : " (not foot; informational)\n");
        out.append("Header footer marker: ")
                .append(footer == FOOTER_MAGIC ? "PRESENT" : "NOT PRESENT")
                .append(" (not used to reject this map)\n");

        if (indexRangeOk && indexLength >= 4) {
            int sampleCount = (int) Math.min(INDEX_SAMPLE_BYTES, indexLength);
            byte[] sample = readAt(resolver, uri, indexOffset, sampleCount);
            ByteBuffer words = ByteBuffer.wrap(sample).order(ByteOrder.LITTLE_ENDIAN);
            out.append("\nINDEX REGION SAMPLE (raw LE uint32; layout not assumed)\n");
            for (int off = 0; off + 4 <= sample.length; off += 4) {
                out.append(String.format(Locale.ROOT, "0x%08X  0x%08X\n",
                        indexOffset + off, u32(words, off)));
            }
            out.append("Sample bytes: ").append(sample.length).append('\n');

            // Research aid only: scan aligned words throughout the bounded index
            // for printable 4-byte sequences. These are candidates, not proven tags.
            byte[] indexBytes = readAt(resolver, uri, indexOffset, (int) indexLength);
            java.util.Map<String, Integer> printableWords = new java.util.TreeMap<>();
            ByteBuffer scan = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int off = 0; off + 4 <= indexBytes.length; off += 4) {
                long value = u32(scan, off);
                String candidate = fourPrintableBytes(value);
                if (candidate != null) {
                    printableWords.put(candidate, printableWords.getOrDefault(candidate, 0) + 1);
                }
            }
            int totalOccurrences = printableWords.values().stream()
                    .mapToInt(Integer::intValue).sum();
            out.append("\nALIGNED PRINTABLE-WORD SCAN (exploratory)\n");
            out.append("Printable 4-byte occurrences: ").append(totalOccurrences).append('\n');
            out.append("Unique printable words: ").append(printableWords.size()).append('\n');

            // Show the most frequent words first; this is easier to inspect than
            // alphabetical output, but frequency alone does not prove tag identity.
            java.util.List<java.util.Map.Entry<String, Integer>> ranked =
                    new java.util.ArrayList<>(printableWords.entrySet());
            ranked.sort((a, b) -> {
                int byCount = Integer.compare(b.getValue(), a.getValue());
                return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
            });
            int shown = Math.min(24, ranked.size());
            for (int i = 0; i < shown; i++) {
                java.util.Map.Entry<String, Integer> entry = ranked.get(i);
                out.append(entry.getKey()).append(" : ").append(entry.getValue()).append('\n');
            }
            if (ranked.size() > shown) {
                out.append("Output capped at ").append(shown).append(" ranked words.\n");
            }
            out.append("Candidates are not decoded tag records; index layout remains unconfirmed.\n");
        } else {
            out.append("\nINDEX SAMPLE SKIPPED: invalid or empty index range.\n");
        }

        out.append("\nRESULT: ");
        out.append(signatureOk && versionOk && declaredLengthOk && indexRangeOk
                ? "HEADER AND INDEX BOUNDS PASS" : "HEADER VALIDATION FAILED");
        out.append("\nThis does not load assets or run gameplay.");
        return out.toString();
    }

    private static byte[] readAt(ContentResolver resolver, Uri uri, long offset, int count)
            throws IOException {
        if (offset < 0 || count < 0) throw new IOException("Negative read range");
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Unable to open selected map file");
            long skipped = 0;
            while (skipped < offset) {
                long n = in.skip(offset - skipped);
                if (n <= 0) {
                    if (in.read() < 0) throw new IOException("Unexpected EOF before offset " + offset);
                    n = 1;
                }
                skipped += n;
            }
            byte[] result = new byte[count];
            int read = 0;
            while (read < count) {
                int n = in.read(result, read, count - read);
                if (n < 0) break;
                read += n;
            }
            if (read != count) throw new IOException("Unexpected EOF at offset " + offset);
            return result;
        }
    }

    private static String fourPrintableBytes(long value) {
        char[] chars = new char[4];
        for (int i = 0; i < 4; i++) {
            int b = (int) ((value >>> (i * 8)) & 0xff);
            if (b < 0x20 || b > 0x7e) return null;
            chars[i] = (char) b;
        }
        return new String(chars);
    }

    private static long u32(ByteBuffer buffer, int offset) {
        return Integer.toUnsignedLong(buffer.getInt(offset));
    }

    private static String hex(long value) {
        return String.format(Locale.ROOT, "0x%08X", value);
    }

    private static String pass(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }
}
