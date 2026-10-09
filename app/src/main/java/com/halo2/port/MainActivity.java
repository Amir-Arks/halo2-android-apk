package com.halo2.port;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        String status;
        try {
            System.loadLibrary("halo2_probe");
            status = "NATIVE LIBRARY LOADED\n\n"
                + "ARM64 Android linker accepted the compiled library.\n\n"
                + "This is an integration test, NOT playable Halo 2.\n"
                + "Xbox APIs, rendering, audio, game startup and assets "
                + "are not yet integrated.";
        } catch (Throwable error) {
            status = "NATIVE LOAD FAILED\n\n"
                + error.getClass().getSimpleName() + ": "
                + error.getMessage();
        }

        TextView text = new TextView(this);
        text.setText(status);
        text.setTextSize(18);
        text.setTypeface(Typeface.MONOSPACE);
        text.setGravity(Gravity.CENTER);
        text.setPadding(28, 28, 28, 28);

        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER);
        layout.addView(text);
        setContentView(layout);
    }
}
