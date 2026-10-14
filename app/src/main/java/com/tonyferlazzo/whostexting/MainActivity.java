package com.tonyferlazzo.whostexting;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
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
    private TextView identityResult;
    private TextView builtInResult;
    private Button ignoreButton;
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
        heading.setText("Who's Texting?");
        heading.setTextSize(24);
        root.addView(heading);
        add(root, "Run built-in test", this::runBuiltInTest);
        builtInResult = new TextView(this);
        builtInResult.setTextSize(18);
        builtInResult.setPadding(0, 16, 0, 24);
        builtInResult.setText("Tap once to test capture, ignore and undo using a sample number. No text message needed.");
        root.addView(builtInResult);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        add(root, "Setup and live status", () ->
            details.setVisibility(details.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        root.addView(details);
        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, 25, 0, 25);
        details.addView(status);
        add(details, "1. Allow notification access", () -> new AlertDialog.Builder(this)
            .setTitle("Sensitive notification access")
            .setMessage("Android gives notification listeners access to notifications from all apps. This app filters to Google Messages sender labels only; Android does not enforce that filter. Grant access only if you trust this app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Android settings", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
            .show());
        add(details, "2. Allow contacts read (optional)", () -> {
            if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 10);
            else refresh();
        });
        add(details, "3. Start fresh test", () -> { MessagesListener.count = 0; MessagesListener.allEvents = 0; MessagesListener.googleEvents = 0; MessagesListener.diagnostic = "Waiting for notification"; MessagesListener.lastSender = "No sender captured"; MessagesListener.testing = true; refresh(); });
        add(details, "Stop test and erase", () -> { MessagesListener.testing = false; MessagesListener.count = 0; MessagesListener.allEvents = 0; MessagesListener.googleEvents = 0; MessagesListener.diagnostic = "Test stopped"; MessagesListener.lastSender = "No sender captured"; refresh(); });
        add(details, "Check last sender in Contacts", this::checkLastSender);
        ignoreButton = new Button(this);
        ignoreButton.setText("Ignore this number");
        ignoreButton.setAllCaps(false);
        ignoreButton.setOnClickListener(v -> ignoreLastSender());
        details.addView(ignoreButton);
        add(details, "Manage ignored numbers", this::manageIgnoredNumbers);
        identityResult = new TextView(this);
        identityResult.setTextSize(18);
        identityResult.setPadding(0, 14, 0, 14);
        identityResult.setText("Identity: not checked");
        details.addView(identityResult);
        add(details, "Refresh status", this::refresh);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
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
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 20 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (Cursor cursor = getContentResolver().query(data.getData(),
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) checkSender(cursor.getString(0), true);
            else identityResult.setText("No phone number selected.");
        } catch (SecurityException ex) {
            identityResult.setText("Cannot read selected contact. Allow Contacts permission.");
        } catch (RuntimeException ex) {
            identityResult.setText("Selected contact could not be read.");
        }
    }
    private void runBuiltInTest() {
        // Reserved fictional sample. Never send a text, inspect contacts or change them.
        final String sample = "(202) 555-0147";
        final String key = MessagesListener.numberKey(sample);
        final boolean wasIgnored = MessagesListener.isIgnored(this, sample);
        final String previousSender = MessagesListener.lastSender;
        final int previousCount = MessagesListener.count;
        final String previousDiagnostic = MessagesListener.diagnostic;
        boolean captured = false, ignored = false, restored = false;
        try {
            MessagesListener.setIgnored(this, key, false);
            captured = MessagesListener.captureSender(this, sample)
                && sample.equals(MessagesListener.lastSender)
                && MessagesListener.count == previousCount + 1;
            MessagesListener.setIgnored(this, key, true);
            int beforeIgnored = MessagesListener.count;
            ignored = MessagesListener.isIgnored(this, "+1 202-555-0147")
                && !MessagesListener.captureSender(this, "+1 202-555-0147")
                && MessagesListener.count == beforeIgnored;
            MessagesListener.setIgnored(this, key, false);
            restored = !MessagesListener.isIgnored(this, sample)
                && MessagesListener.captureSender(this, sample)
                && MessagesListener.count == beforeIgnored + 1;
            builtInResult.setText((captured && ignored && restored
                ? "PASS — capture, ignore and undo work."
                : "Test needs attention. Capture: " + captured + "; ignore: " + ignored + "; undo: " + restored)
                + "\n\nSample test only. This does not verify incoming Google Messages notifications. Your contacts and real event counts are unchanged.");
        } catch (RuntimeException error) {
            builtInResult.setText("Built-in test could not finish. No real text message was sent.");
        } finally {
            MessagesListener.setIgnored(this, key, wasIgnored);
            MessagesListener.lastSender = previousSender;
            MessagesListener.count = previousCount;
            MessagesListener.diagnostic = previousDiagnostic;
            refresh();
        }
    }
    private void checkLastSender() {
        checkSender(MessagesListener.lastSender, false);
    }
    private void checkSender(String sender, boolean simulation) {
        if (sender == null) sender = "";
        String prefix = simulation ? "SIMULATED TEST — no message received.\\n" : "";
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            identityResult.setText(prefix + "Contacts permission required. Tap Allow contacts read, then retry.");
            return;
        }
        if (MessagesListener.isIgnored(this, sender)) {
            identityResult.setText(prefix + "This number is ignored.");
            return;
        }
        String digits = sender.replaceAll("[^0-9+]", "");
        if (digits.length() < 7 || digits.length() > 16 || !PhoneNumberUtils.isGlobalPhoneNumber(digits)) {
            identityResult.setText(prefix + "No recognizable phone number.");
            return;
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(digits));
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                identityResult.setText(prefix + "Saved contact: " + cursor.getString(0));
            } else {
                identityResult.setText(prefix + "Unknown sender: " + sender +
                    "\\nNot found in device Contacts. No business identity verified.");
            }
        } catch (SecurityException ex) {
            identityResult.setText(prefix + "Contacts access was denied.");
        } catch (RuntimeException ex) {
            identityResult.setText(prefix + "Contacts lookup unavailable. No identity inferred.");
        }
    }
    private void ignoreLastSender() {
        String sender = MessagesListener.lastSender;
        String number = MessagesListener.numberKey(sender);
        if (number.isEmpty()) {
            identityResult.setText("No recognizable sender number to ignore.");
            return;
        }
        MessagesListener.setIgnored(this, number, true);
        MessagesListener.lastSender = "No sender captured";
        identityResult.setText("Ignored: " + number + "\nFuture identification prompts from this number will be skipped. Texts will still arrive in Google Messages.");
        refresh();
    }
    private void manageIgnoredNumbers() {
        java.util.Set<String> ignored = MessagesListener.ignoredNumbers(this);
        if (ignored.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Ignored numbers")
                .setMessage("No numbers ignored.").setPositiveButton("OK", null).show();
            return;
        }
        String[] numbers = ignored.toArray(new String[0]);
        java.util.Arrays.sort(numbers);
        new AlertDialog.Builder(this).setTitle("Tap a number to stop ignoring it")
            .setItems(numbers, (dialog, which) -> {
                MessagesListener.setIgnored(this, numbers[which], false);
                identityResult.setText("Restored: " + numbers[which] + "\nNew notifications from this number can be checked again.");
                refresh();
            }).setNegativeButton("Close", null).show();
    }
    private void refresh() {
        if (status == null) return;
        ignoreButton.setEnabled(!MessagesListener.numberKey(MessagesListener.lastSender).isEmpty());
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
            "\nIgnored numbers: " + MessagesListener.ignoredNumbers(this).size() +
            "\n\nAndroid may restrict notification access for sideloaded APKs. Do not disable Play Protect globally.");
    }
}
