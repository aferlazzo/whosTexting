package com.tonyferlazzo.whostexting;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class MessagesListener extends NotificationListenerService {
    public static volatile boolean connected = false;
    public static volatile String lastSender = "No sender captured";
    public static volatile int count = 0;
    public static volatile boolean testing = false;
    private static final String MESSAGES = "com.google.android.apps.messaging";

    @Override public void onListenerConnected() {
        connected = true;
        super.onListenerConnected();
    }
    @Override public void onListenerDisconnected() {
        connected = false;
        super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!testing || sbn == null || !MESSAGES.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle extras = n.extras;
        if (extras == null) return;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null) return;
        String sender = title.toString().trim();
        // Never inspect, retain or transmit message content.
        if (sender.isEmpty() || sender.length() > 100 || sender.contains(",") || sender.contains(" • ")) return;
        lastSender = sender;
        count++;
    }
}
