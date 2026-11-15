package com.reelshield.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle b) { super.onCreate(b); build(); }
    @Override protected void onResume() { super.onResume(); if (status != null) updateStatus(); }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 55, 40, 40);

        TextView title = new TextView(this);
        title.setText("ReelShield — v2.0 Focus Mode");
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 18, 0, 18);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button enable = new Button(this);
        enable.setText("Open Accessibility Settings");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(enable, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("Instagram focus controls\n\n• Home and Reels navigation entries are masked\n• Main Home feed area is covered; Stories are intended to remain available\n• Profile, Messages and Create remain accessible\n• Reels are blocked when detected from supported entry points\n• Back and Home remain usable\n\nOverlay alignment and Instagram compatibility require device testing");
        info.setTextSize(16);
        info.setPadding(0, 20, 0, 20);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        updateStatus();
    }

    private void updateStatus() {
        boolean on = false;
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            ComponentName me = new ComponentName(this, ShieldAccessibilityService.class);
            on = enabled != null && enabled.toLowerCase().contains(me.flattenToString().toLowerCase());
        } catch (Exception ignored) { }
        status.setText(on ? "● Protection service is ON" : "○ Protection service is OFF");
    }
}
