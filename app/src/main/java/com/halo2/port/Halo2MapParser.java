package com.halo2.port;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Locale;

/**
 * Read-only structural validator for a Halo 2 cache map.
 * This validates header/index bounds only; it does not decode tags or render a level.
 */
final class Halo2MapParser {
    private static final long HEADER_SIZE = 0x800L;
    private static final long MAX_INDEX_SAMPLE = 128L;

    private Halo2MapParser() {}

    static String inspect(ContentResolver resolver, Uri uri, String name) {
        try (ParcelFileDescriptor pfd =
                     resolver.openFileDescriptor(uri, "r")) {
            if (pfd == null) return "ERROR: unable to open map file";
            try (FileInputStream input = new FileInputStream(pfd.getFileDescriptor())) {
                FileChannel channel = input.getChannel();
                long actualLength = channel.size();
                if (actualLength < HEADER_SIZE) {
                    return "INVALID: file is smaller than the 0x800-byte cache header";
                }

                ByteBuffer h = ByteBuffer.allocate((int) HEADER_SIZE)
                        .order(ByteOrder.LITTLE_ENDIAN);
                channel.position(0);
                readFully(channel, h);
                h.flip();

                long signature = u32(h, 0x00);
                long version = u32(h, 0x04);
                long declaredLength = u32(h, 0x08);
                long indexOffset = u32(h, 0x10);
                long indexLength = u32(h, 0x14);
                long rawDataLength = u32(h, 0x18);
                long allocationLength = u32(h, 0x1c);
                long footer = u32(h, 0x7fc);

                StringBuilder out = new StringBuilder();
                out.append("HALO 2 MAP STRUCTURE CHECK\n");
                out.append("File: ").append(name).append("\n");
                out.append(String.format(Locale.ROOT,
                        "Header: %s (0x%08X)\n",
                        signature == 0x68656164L ? "head" : "UNKNOWN", signature));
                out.append("Version: ").append(version).append("\n");
                out.append("Declared file length: ").append(declaredLength).append(" bytes\n");
                out.append("Actual file length: ").append(actualLength).append(" bytes\n");
                out.append(String.format(Locale.ROOT,
                        "Index offset: 0x%08X (%d)\n", indexOffset, indexOffset));
                out.append(String.format(Locale.ROOT,
                        "Index length: 0x%08X (%d)\n", indexLength, indexLength));
                out.append(String.format(Locale.ROOT,
                        "Raw-data length field: 0x%08X\n", rawDataLength));
                out.append(String.format(Locale.ROOT,
                        "Allocation-length field: 0x%08X\n", allocationLength));
                out.append(String.format(Locale.ROOT,
                        "Header footer: %s (0x%08X)\n",
                        footer == 0x666f6f74L ? "foot" : "UNKNOWN", footer));

                boolean signatureOk = signature == 0x68656164L;
                boolean footerOk = footer == 0x666f6f74L;
                boolean versionOk = version == 8;
                boolean lengthOk = declaredLength == actualLength;
                boolean indexOk = indexOffset >= HEADER_SIZE
                        && indexLength > 0
                        && indexOffset <= actualLength
                        && indexLength <= actualLength - indexOffset;

                out.append("\nVALIDATION\n");
                out.append("Header signature: ").append(pass(signatureOk)).append("\n");
                out.append("Header footer: ").append(pass(footerOk)).append("\n");
                out.append("Supported version 8: ").append(pass(versionOk)).append("\n");
                out.append("Declared file length: ").append(pass(lengthOk)).append("\n");
                out.append("Index bounds: ").append(pass(indexOk)).append("\n");

                if (indexOk) {
                    int sampleSize = (int) Math.min(MAX_INDEX_SAMPLE, indexLength);
                    ByteBuffer sample = ByteBuffer.allocate(sampleSize)
                            .order(ByteOrder.LITTLE_ENDIAN);
                    channel.position(indexOffset);
                    readFully(channel, sample);
                    sample.flip();
                    out.append("\nINDEX REGION SAMPLE (little-endian uint32)\n");
                    for (int pos = 0; pos + 4 <= sample.limit(); pos += 4) {
                        out.append(String.format(Locale.ROOT,
                                "0x%08X  0x%08X\n", indexOffset + pos, sample.getInt(pos)));
                    }
                    long sentinelCount = 0;
                    long highBitCount = 0;
                    long alignedValueCount = 0;
                    long printableWordCount = 0;
                    StringBuilder printable = new StringBuilder();
                    for (int pos = 0; pos + 4 <= sample.limit(); pos += 4) {
                        long word = Integer.toUnsignedLong(sample.getInt(pos));
                        if (word == 0xFFFFFFFFL) sentinelCount++;
                        if ((word & 0x80000000L) != 0) highBitCount++;
                        if ((word & 3L) == 0 && word != 0) alignedValueCount++;
                        boolean allPrintable = true;
                        for (int shift = 0; shift < 32; shift += 8) {
                            int ch = (int) ((word >>> shift) & 0xFF);
                            if (ch < 0x20 || ch > 0x7E) {
                                allPrintable = false;
                                break;
                            }
                        }
                        if (allPrintable) {
                            printableWordCount++;
                            if (printable.length() < 240) {
                                if (printable.length() > 0) printable.append(", ");
                                printable.append(String.format(Locale.ROOT,
                                        "0x%08X='%c%c%c%c'", word,
                                        (char) (word & 0xFF),
                                        (char) ((word >>> 8) & 0xFF),
                                        (char) ((word >>> 16) & 0xFF),
                                        (char) ((word >>> 24) & 0xFF)));
                            }
                        }
                    }
                    out.append("\nINDEX SAMPLE DIAGNOSTICS (exploratory, not tag decoding)\n");
                    out.append("Sample bytes: ").append(sample.limit()).append("\n");
                    out.append("0xFFFFFFFF words: ").append(sentinelCount).append("\n");
                    out.append("High-bit-set words: ").append(highBitCount).append("\n");
                    out.append("Nonzero 4-byte-aligned values: ").append(alignedValueCount).append("\n");
                    out.append("Printable 4-byte words: ").append(printableWordCount).append("\n");
                    if (printable.length() > 0) {
                        out.append("Printable candidates: ").append(printable).append("\n");
                    }
                    out.append("These counts describe only the first sample; entry meanings are not assumed.\n");
                } else {
                    out.append("\nIndex sample skipped because bounds are invalid.\n");
                }

                boolean valid = signatureOk && footerOk && versionOk && lengthOk && indexOk;
                out.append("\nRESULT: ").append(valid
                        ? "HEADER AND INDEX BOUNDS PASS"
                        : "STRUCTURAL VALIDATION FAILED");
                out.append("\nThis does not yet parse tag entries or load gameplay.");
                return out.toString();
            }
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            return "ERROR reading map: " + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : " — " + error.getMessage());
        }
    }

    private static long u32(ByteBuffer buffer, int offset) {
        return Integer.toUnsignedLong(buffer.getInt(offset));
    }

    private static String pass(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    private static void readFully(FileChannel channel, ByteBuffer target) throws IOException {
        while (target.hasRemaining()) {
            int count = channel.read(target);
            if (count < 0) throw new IOException("unexpected end of file");
        }
    }
}
