package com.tonyferlazzo.whostexting;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdater = new Runnable() {
        @Override public void run() {
            refresh();
            statusHandler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 40, 36, 20);
        TextView heading = new TextView(this);
        heading.setText("Who's Texting? — permission test");
        heading.setTextSize(24);
        root.addView(heading);
        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, 25, 0, 25);
        root.addView(status);
        add(root, "1. Allow notification access", () -> new AlertDialog.Builder(this)
            .setTitle("Sensitive notification access")
            .setMessage("Android gives notification listeners access to notifications from all apps. This app filters to Google Messages sender labels only; Android does not enforce that filter. Grant access only if you trust this app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Android settings", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
            .show());
        add(root, "2. Allow contacts read (optional)", () -> {
            if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 10);
            else refresh();
        });
        add(root, "3. Start fresh test", () -> { MessagesListener.count = 0; MessagesListener.allEvents = 0; MessagesListener.googleEvents = 0; MessagesListener.diagnostic = "Waiting for notification"; MessagesListener.lastSender = "No sender captured"; MessagesListener.testing = true; refresh(); });
        add(root, "Stop test and erase", () -> { MessagesListener.testing = false; MessagesListener.count = 0; MessagesListener.allEvents = 0; MessagesListener.googleEvents = 0; MessagesListener.diagnostic = "Test stopped"; MessagesListener.lastSender = "No sender captured"; refresh(); });
        add(root, "Refresh status", this::refresh);
        setContentView(root);
        refresh();
    }
    private void add(LinearLayout root, String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false);
        b.setOnClickListener(v -> action.run()); root.addView(b);
    }
    @Override protected void onResume() {
        super.onResume();
        statusHandler.removeCallbacks(statusUpdater);
        statusUpdater.run();
    }
    @Override protected void onPause() {
        statusHandler.removeCallbacks(statusUpdater);
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); refresh();
    }
    private void refresh() {
        if (status == null) return;
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        boolean access = manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(this, MessagesListener.class));
        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        status.setText("Notification access: " + (access ? "Allowed" : "Not allowed") +
            "\nListener connected: " + MessagesListener.connected +
            "\nContacts permission: " + (contacts ? "Allowed" : "Not allowed") +
            "\nTest: " + (MessagesListener.testing ? "Running" : "Stopped") +
            "\nAll notification events: " + MessagesListener.allEvents +
            "\nGoogle Messages notifications: " + MessagesListener.googleEvents +
            "\nDiagnostic: " + MessagesListener.diagnostic +
            "\nGoogle Messages events: " + MessagesListener.count +
            "\nLast sender label: " + MessagesListener.lastSender +
            "\n\nAndroid may restrict notification access for sideloaded APKs. Do not disable Play Protect globally.");
    }
}
