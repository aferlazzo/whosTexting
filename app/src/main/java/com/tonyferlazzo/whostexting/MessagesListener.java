package com.tonyferlazzo.whostexting;

import android.app.Notification;
import android.content.Context;
import android.telephony.PhoneNumberUtils;
import java.util.HashSet;
import java.util.Set;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class MessagesListener extends NotificationListenerService {
    public static volatile boolean connected = false;
    public static volatile String lastSender = "No sender captured";
    public static volatile int count = 0;
    public static volatile boolean testing = false;
    public static volatile int allEvents = 0;
    public static volatile int googleEvents = 0;
    public static volatile String diagnostic = "Waiting for notification";
    private static final String MESSAGES = "com.google.android.apps.messaging";

    static String numberKey(String sender) {
        if (sender == null || !sender.matches("[+0-9().\\s-]+")) return "";
        String number = PhoneNumberUtils.normalizeNumber(sender);
        if (!number.matches("\\+?[0-9]{3,15}")) return "";
        // Treat common US local and country-code forms as the same sender.
        if (number.matches("[0-9]{10}")) return "+1" + number;
        if (number.matches("1[0-9]{10}")) return "+" + number;
        return number;
    }
    static Set<String> ignoredNumbers(Context context) {
        return new HashSet<>(context.getSharedPreferences("sender_preferences", Context.MODE_PRIVATE)
            .getStringSet("ignored_numbers", java.util.Collections.emptySet()));
    }
    static boolean isIgnored(Context context, String sender) {
        String number = numberKey(sender);
        return !number.isEmpty() && ignoredNumbers(context).contains(number);
    }
    static void setIgnored(Context context, String number, boolean ignored) {
        Set<String> numbers = ignoredNumbers(context);
        if (ignored) numbers.add(number); else numbers.remove(number);
        context.getSharedPreferences("sender_preferences", Context.MODE_PRIVATE)
            .edit().putStringSet("ignored_numbers", numbers).apply();
    }

    @Override public void onListenerConnected() {
        connected = true;
        super.onListenerConnected();
    }
    @Override public void onListenerDisconnected() {
        connected = false;
        super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!testing || sbn == null) return;
        allEvents++;
        if (!MESSAGES.equals(sbn.getPackageName())) return;
        googleEvents++;
        diagnostic = "Google Messages notification received";
        Notification n = sbn.getNotification();
        if (n == null) { diagnostic = "Notification object missing"; return; }
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) { diagnostic = "Group summary filtered"; return; }
        Bundle extras = n.extras;
        if (extras == null) { diagnostic = "No notification extras"; return; }
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null) { diagnostic = "Sender title missing"; return; }
        String sender = title.toString().trim();
        // Never inspect, retain or transmit message content.
        if (sender.isEmpty() || sender.length() > 100 || sender.contains(",") || sender.contains(" • ")) {
            diagnostic = "Sender title filtered";
            return;
        }
        captureSender(this, sender);
    }

    // Shared by real notification handling and the clearly labeled built-in test.
    static boolean captureSender(Context context, String sender) {
        if (isIgnored(context, sender)) {
            diagnostic = "Ignored sender skipped";
            return false;
        }
        lastSender = sender;
        count++;
        diagnostic = "Sender captured";
        return true;
    }
}
