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
    public static volatile String lastNumber = "";
    public static volatile int count = 0;
    public static volatile boolean testing = false;
    public static volatile int allEvents = 0;
    public static volatile int googleEvents = 0;
    public static volatile String diagnostic = "Waiting for notification";
    private static volatile MessagesListener activeListener;
    private static final String MESSAGES = "com.google.android.apps.messaging";

    static String numberKey(String sender) {
        if (sender != null && sender.startsWith("tel:")) sender = sender.substring(4);
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

    static void setTesting(Context context, boolean enabled) {
        testing = enabled;
        context.getSharedPreferences("sender_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("live_test_running", enabled).apply();
    }

    @Override public void onCreate() {
        super.onCreate();
        testing = getSharedPreferences("sender_preferences", Context.MODE_PRIVATE)
            .getBoolean("live_test_running", true);
        java.util.List<SenderStore.Sender> history = SenderStore.read(this);
        if (!history.isEmpty()) { lastSender = history.get(0).label; lastNumber = history.get(0).number; }
    }

    @Override public void onListenerConnected() {
        activeListener = this;
        connected = true;
        super.onListenerConnected();
        if (testing) checkCurrentMessages();
    }
    @Override public void onListenerDisconnected() {
        connected = false;
        activeListener = null;
        super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!testing || sbn == null) return;
        allEvents++;
        if (!MESSAGES.equals(sbn.getPackageName())) return;
        googleEvents++;
        diagnostic = "Google Messages notification received";
        String sender = senderTitle(sbn.getNotification());
        if (sender != null) {
            String number = senderNumber(sbn.getNotification(), sender);
            if (isIgnored(this, number)) { diagnostic = "Ignored sender skipped"; return; }
            if (captureSender(this, sender)) {
                lastNumber = number;
                SenderStore.record(this, sender, number);
            }
        }
    }

    @Override public void onDestroy() {
        if (activeListener == this) {
            activeListener = null;
            connected = false;
        }
        super.onDestroy();
    }

    private static String senderTitle(Notification n) {
        if (n == null) { diagnostic = "Notification object missing"; return null; }
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            diagnostic = "Group summary filtered"; return null;
        }
        Bundle extras = n.extras;
        if (extras == null) { diagnostic = "No notification extras"; return null; }
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null) { diagnostic = "Sender title missing"; return null; }
        String sender = title.toString().trim();
        // Only inspect sender labels. Never inspect, retain or transmit message bodies.
        if (sender.isEmpty() || sender.length() > 160) {
            diagnostic = "Sender title filtered"; return null;
        }
        return sender;
    }

    private static String senderNumber(Notification notification, String title) {
        String direct = numberKey(title);
        if (!direct.isEmpty()) return direct;
        if (notification == null || notification.extras == null) return "";
        Bundle extras = notification.extras;
        // Inspect sender metadata only, never the text members of message Bundles.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.os.Parcelable[] messages = extras.getParcelableArray("android.messages");
            if (messages != null && messages.length > 0 && messages[messages.length - 1] instanceof Bundle) {
                Bundle last = (Bundle)messages[messages.length - 1];
                android.os.Parcelable value = last.getParcelable("sender_person");
                if (value instanceof android.app.Person) {
                    String phone = numberKey(((android.app.Person)value).getUri());
                    if (!phone.isEmpty()) return phone;
                }
            }
            java.util.ArrayList<android.app.Person> people = extras.getParcelableArrayList("android.people.list");
            if (people != null && people.size() == 1) {
                String phone = numberKey(people.get(0).getUri());
                if (!phone.isEmpty()) return phone;
            }
        }
        String[] people = extras.getStringArray("android.people");
        return people != null && people.length == 1 ? numberKey(people[0]) : "";
    }

    static String checkCurrentMessages() {
        MessagesListener listener = activeListener;
        if (listener == null || !connected)
            return "Listener is not connected. Check notification access on the main screen.";
        try {
            StatusBarNotification[] notifications = listener.getActiveNotifications();
            if (notifications == null)
                return "Android did not return current notifications.";
            java.util.Arrays.sort(notifications, (left, right) ->
                Long.compare(left.getPostTime(), right.getPostTime()));
            int messages = 0;
            java.util.Set<String> recovered = new java.util.HashSet<>();
            for (StatusBarNotification notification : notifications) {
                if (!MESSAGES.equals(notification.getPackageName())) continue;
                messages++;
                String sender = senderTitle(notification.getNotification());
                if (sender == null) continue;
                String number = senderNumber(notification.getNotification(), sender);
                if (isIgnored(listener, sender) || isIgnored(listener, number)) continue;
                // A snapshot is separate from live event counters.
                lastSender = sender;
                lastNumber = number;
                SenderStore.record(listener, sender, number, notification.getPostTime());
                recovered.add(number.isEmpty() ? "label:" + sender : number);
            }
            if (!recovered.isEmpty()) {
                diagnostic = "Recovered " + recovered.size() + " sender(s) from current Google Messages notifications";
                return diagnostic + "\nLatest sender: " + lastSender
                    + "\nAll recovered senders are in Recent senders. These are currently showing notifications, not historical texts.";
            }
            diagnostic = messages == 0 ? "No current Google Messages notifications accessible"
                : "Current Google Messages notifications were ignored or filtered";
            return messages == 0
                ? "No current Google Messages notification is accessible to this app. A visible notification may be excluded by Android's access settings."
                : "Google Messages notifications are present, but no individual unignored sender label is available.";
        } catch (SecurityException error) {
            return "Android denied access to current notifications. Check notification access on the main screen.";
        } catch (RuntimeException error) {
            return "Current notification check could not finish. Your messages and contacts were not changed.";
        }
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

