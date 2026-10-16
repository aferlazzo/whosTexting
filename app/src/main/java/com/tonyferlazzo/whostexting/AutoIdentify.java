package com.tonyferlazzo.whostexting;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional, consented lookup of new unknown numbers. Message clues are derived locally; only numbers reach directories. */
final class AutoIdentify {
    static final String CHANNEL = "sender_identity";
    static final String TEST_CHANNEL = "sender_identity_test";
    static final int TEST_ID = -1048606;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private AutoIdentify() {}

    static boolean enabled(Context context) {
        return context.getSharedPreferences("sender_preferences", Context.MODE_PRIVATE)
            .getBoolean("auto_lookup_consent", false);
    }

    interface Lookup { BusinessLookup.Result lookup(String number); }

    static void submitWithClues(Context context, String number, String clues) {
        if (clues != null && !clues.isEmpty()) {
            if (ContactActions.canRead(context) && !MessagesListener.isIgnored(context, number)) {
                try {
                    if (ContactActions.existingName(context, number).isEmpty())
                        showClues(context, number, clues);
                } catch (RuntimeException unavailable) { /* Fail closed on contact access failure. */ }
            }
            return; // Useful local clues avoid unnecessary external lookup.
        }
        submit(context, number);
    }

    private static void showClues(Context context, String number, String clues) {
        // Local analysis does not require consent to external directory lookup.
        show(context, number, "Text purpose (unverified)", number + " · " + clues, false);
    }

    static void submit(Context context, String number) {
        submit(context, number, BusinessLookup::lookup);
    }

    static void submit(Context context, String number, Lookup lookup) {
        if (!enabled(context) || !ContactActions.canRead(context) || number == null || number.isEmpty()
                || number.replaceAll("[^0-9]", "").length() < 7
                || MessagesListener.isIgnored(context, number)) return;
        String key = MessagesListener.numberKey(number);
        if (key.isEmpty()) return;
        synchronized (IN_FLIGHT) {
            if (!IN_FLIGHT.add(key)) return;
        }
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                if (!allowed(app, key)) return;
                BusinessLookup.Result result = lookup.lookup(key);
                if (!allowed(app, key)) return;
                String title, detail;
                if (result.matches.size() == 1) {
                    title = "Possible sender: " + result.matches.get(0).name;
                    detail = key + " · Unverified " + result.matches.get(0).source + " listing. Tap to review.";
                } else if (result.matches.size() > 1) {
                    title = "Several possible business matches";
                    detail = key + " · Conflicting public listings. Tap to review.";
                } else if (!result.unavailable.isEmpty()) {
                    title = "Sender identity unavailable";
                    detail = key + " · Public directory lookup incomplete. Tap to review or retry.";
                } else {
                    title = "Sender identity unknown";
                    detail = key + " · No reliable public-directory match found.";
                }
                show(app, key, title, detail);
            } catch (Exception ignored) {
                show(app, key, "Sender identity unavailable",
                    key + " · Lookup could not finish. No identity assumed.");
            } finally {
                synchronized (IN_FLIGHT) { IN_FLIGHT.remove(key); }
            }
        });
    }

    private static boolean allowed(Context context, String key) {
        try {
            return enabled(context) && ContactActions.canRead(context)
                && !MessagesListener.isIgnored(context, key)
                && ContactActions.existingName(context, key).isEmpty();
        } catch (RuntimeException unavailable) { return false; }
    }

    private static void show(Context context, String number, String title, String detail) {
        show(context, number, title, detail, true);
    }

    private static void show(Context context, String number, String title, String detail, boolean external) {
        if (external && !allowed(context, number)) return;
        if (!ContactActions.canRead(context) || MessagesListener.isIgnored(context, number)) return;
        try { if (!ContactActions.existingName(context, number).isEmpty()) return; }
        catch (RuntimeException unavailable) { return; }
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("selected_number", number);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        post(context, CHANNEL, number.hashCode(), title, detail, open);
    }

    static boolean alertsEnabled(Context context, String channel) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationChannel existing = manager.getNotificationChannel(channel);
        return existing == null || existing.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static boolean showTest(Context context, String number, String clues) {
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("show_workflow_test", true);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return post(context, TEST_CHANNEL, TEST_ID, "WT TEST — simulated unknown sender",
            "FICTIONAL TEST " + number + " · " + clues, open);
    }

    private static boolean post(Context context, String channel, int id, String title, String detail, Intent open) {
        if (!alertsEnabled(context, channel)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        manager.createNotificationChannel(new NotificationChannel(channel,
            channel.equals(TEST_CHANNEL) ? "Workflow test alerts" : "Unknown sender identification",
            NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent intent = PendingIntent.getActivity(context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(detail)
            .setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(intent).setAutoCancel(true).build();
        try {
            manager.notify(id, notification);
            return true; // Android accepted the post; visibility is subject to system settings and DND.
        } catch (RuntimeException blocked) { return false; }
    }
}
