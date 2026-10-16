package com.tonyferlazzo.whostexting;

import android.Manifest;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AutoIdentifyTest {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    void consent(boolean value) {
        context.getSharedPreferences("sender_preferences", 0).edit().putBoolean("auto_lookup_consent", value).commit();
    }
    void waitFor(java.util.function.BooleanSupplier condition) {
        long end = SystemClock.elapsedRealtime() + 10000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
        assertTrue(condition.getAsBoolean());
    }
    @Test public void automaticLookupProtectsContactsAndPostsHonestAlerts() throws Exception {
        String known = "+12025550198", unknown = "+12025550123";
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancelAll();
        AtomicInteger calls = new AtomicInteger();
        try {
            consent(false);
            AutoIdentify.submit(context, unknown, key -> { calls.incrementAndGet(); return new BusinessLookup.Result(); });
            consent(true);
            Context denied = new ContextWrapper(context) {
                @Override public int checkSelfPermission(String permission) {
                    return Manifest.permission.READ_CONTACTS.equals(permission) ? PackageManager.PERMISSION_DENIED : super.checkSelfPermission(permission);
                }
                @Override public Context getApplicationContext() { return this; }
            };
            AutoIdentify.submit(denied, unknown, key -> { calls.incrementAndGet(); return new BusinessLookup.Result(); });
            MessagesListener.setIgnored(context, unknown, true);
            AutoIdentify.submit(context, unknown, key -> { calls.incrementAndGet(); return new BusinessLookup.Result(); });
            MessagesListener.setIgnored(context, unknown, false);
            AutoIdentify.submit(context, "12345", key -> { calls.incrementAndGet(); return new BusinessLookup.Result(); });
            Intent seed = new Intent().setComponent(new ComponentName("com.google.android.apps.messaging",
                "com.tonyferlazzo.whostexting.fixture.SenderActivity"));
            seed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("sender", "WT Known Contact")
                .putExtra("phone", known).putExtra("seed_contact", true).putExtra("messaging_style", true);
            context.startActivity(seed);
            waitFor(() -> !ContactActions.existingName(context, known).isEmpty());
            AutoIdentify.submit(context, known, key -> { calls.incrementAndGet(); return new BusinessLookup.Result(); });
            CountDownLatch called = new CountDownLatch(1);
            AutoIdentify.submit(context, unknown, key -> {
                assertEquals(unknown, key);
                calls.incrementAndGet(); called.countDown();
                BusinessLookup.Result result = new BusinessLookup.Result();
                result.unavailable.add("Wikidata"); return result;
            });
            assertTrue("Unknown sender must be automatically looked up", called.await(10, TimeUnit.SECONDS));
            waitFor(() -> java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n ->
                n.getId() == unknown.hashCode() && "Sender identity unavailable".contentEquals(
                    n.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE))));
            assertEquals("Only the unknown, consented number may reach lookup", 1, calls.get());
            assertTrue(java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n ->
                n.getId() == unknown.hashCode() && n.getNotification().contentIntent != null));
        } finally { consent(false); manager.cancelAll(); MessagesListener.setIgnored(context, unknown, false); }
    }
    @Test public void identityAlertsDistinguishMatchesConflictsAndNoMatch() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancelAll(); consent(true);
        try {
            for (int outcome = 0; outcome < 3; outcome++) {
                String number = "+1202555013" + outcome;
                final int size = outcome;
                AutoIdentify.submit(context, number, key -> {
                    BusinessLookup.Result result = new BusinessLookup.Result();
                    for (int index = 0; index < size; index++) result.matches.add(new BusinessLookup.Match(
                        "Synthetic Business " + index, "https://www.wikidata.org/wiki/Q123", "", "Wikidata"));
                    return result;
                });
                String expected = outcome == 0 ? "Sender identity unknown"
                    : outcome == 1 ? "Possible sender: Synthetic Business 0" : "Several possible business matches";
                waitFor(() -> java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n ->
                    n.getId() == number.hashCode() && expected.contentEquals(
                        n.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE))));
            }
        } finally { consent(false); manager.cancelAll(); }
    }

}
