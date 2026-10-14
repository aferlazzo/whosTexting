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
    public static volatile int allEvents = 0;
    public static volatile int googleEvents = 0;
    public static volatile String diagnostic = "Waiting for notification";
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
        lastSender = sender;
        count++;
        diagnostic = "Sender captured";
    }
}
