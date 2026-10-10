package com.halo2.port;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import java.io.InputStream;
import java.io.IOException;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.documentfile.provider.DocumentFile;

public class MainActivity extends Activity {
    private static final int PICK_GAME_FOLDER = 42;
    private TextView status;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 28, 24, 28);

        TextView title = new TextView(this);
        title.setText("HALO 2 · NATIVE ANDROID");
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, 16);
        page.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Milestone 1: verify your extracted game files. Select the folder that contains default.xbe and the maps directory. This app does not run gameplay yet.");
        intro.setTextSize(16);
        intro.setPadding(0, 0, 0, 18);
        page.addView(intro);

        Button choose = new Button(this);
        choose.setText("SELECT HALO 2 GAME FOLDER");
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_GAME_FOLDER);
        });
        page.addView(choose);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTypeface(Typeface.MONOSPACE);
        status.setText("\nWaiting for game folder…");
        status.setPadding(0, 20, 0, 0);
        page.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GAME_FOLDER || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }

        DocumentFile root = DocumentFile.fromTreeUri(this, uri);
        if (root == null || !root.isDirectory()) {
            status.setText("ERROR: Android could not open that folder.");
            return;
        }

        DocumentFile xbe = findChildIgnoreCase(root, "default.xbe");
        DocumentFile maps = findChildIgnoreCase(root, "maps");

        int mapFiles = 0;
        long mapBytes = 0;
        DocumentFile sampleMap = null;
        if (maps != null && maps.isDirectory()) {
            for (DocumentFile file : maps.listFiles()) {
                if (file.isFile() && file.getName() != null
                        && file.getName().toLowerCase().endsWith(".map")) {
                    mapFiles++;
                    if (file.length() > 0) mapBytes += file.length();
                    if (sampleMap == null) sampleMap = file;
                }
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("ASSET CHECK\n");
        report.append("Folder: ").append(root.getName()).append("\n\n");
        report.append("default.xbe: ").append(xbe != null && xbe.isFile() ? "FOUND" : "MISSING").append("\n");
        report.append("maps folder: ").append(maps != null && maps.isDirectory() ? "FOUND" : "MISSING").append("\n");
        report.append("Top-level .map files: ").append(mapFiles).append("\n");
        report.append("Map data size: ").append(formatBytes(mapBytes)).append("\n");
        if (sampleMap != null) {
            report.append("\nSAMPLE MAP HEADER (first 64 bytes)\n");
            report.append("File: ").append(sampleMap.getName()).append("\n");
            report.append(readHeaderHex(sampleMap)).append("\n");
        }

        if (xbe != null && maps != null && maps.isDirectory() && mapFiles > 0) {
            report.append("RESULT: BASIC ASSET STRUCTURE FOUND\n");
            report.append("Next: parse map headers and validate engine data structures.");
        } else {
            report.append("RESULT: INCOMPLETE OR WRONG FOLDER\n");
            report.append("Choose the extracted Halo 2 folder containing default.xbe and maps/*.map.");
        }
        report.append("\n\nThis is file discovery only—not gameplay or a complete native port.");
        status.setText(report.toString());
    }

    private DocumentFile findChildIgnoreCase(DocumentFile parent, String wanted) {
        for (DocumentFile child : parent.listFiles()) {
            if (child.getName() != null && child.getName().equalsIgnoreCase(wanted)) return child;
        }
        return null;
    }

    private String readHeaderHex(DocumentFile file) {
        try (InputStream in = getContentResolver().openInputStream(file.getUri())) {
            if (in == null) return "ERROR: unable to open map file";
            byte[] bytes = new byte[64];
            int count = 0;
            while (count < bytes.length) {
                int n = in.read(bytes, count, bytes.length - count);
                if (n < 0) break;
                count += n;
            }
            if (count == 0) return "(empty file)";
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (i > 0 && i % 16 == 0) hex.append('\n');
                hex.append(String.format("%02X ", bytes[i] & 0xff));
            }
            return hex.toString();
        } catch (IOException | SecurityException error) {
            return "ERROR reading header: " + error.getClass().getSimpleName();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format("%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
        if (bytes >= 1024L * 1024L) return String.format("%.1f MiB", bytes / (1024.0 * 1024.0));
        if (bytes >= 1024L) return String.format("%.1f KiB", bytes / 1024.0);
        return bytes + " B";
    }
}
