package com.tonyferlazzo.whostexting;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NotificationDeliveryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private interface Condition { boolean satisfied(); }

    private void await(String message, Condition condition) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (!condition.satisfied() && SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(100);
        assertTrue(message + "; diagnostic=" + MessagesListener.diagnostic, condition.satisfied());
    }

    private void post(String sender, boolean messagingStyle) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.google.android.apps.messaging",
            "com.tonyferlazzo.whostexting.fixture.SenderActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("sender", sender);
        intent.putExtra("messaging_style", messagingStyle);
        context.startActivity(intent);
    }

    @Test public void androidDeliversCapturesIgnoresRestoresAndSnapshots() {
        String flat = "(202) 555-0147";
        String conversation = "(202) 555-0188";
        String key = MessagesListener.numberKey(flat);
        await("Android notification listener must connect", () -> MessagesListener.connected);
        MessagesListener.setIgnored(context, key, false);
        MessagesListener.setTesting(context, true);
        MessagesListener.count = 0;
        MessagesListener.googleEvents = 0;
        MessagesListener.lastSender = "No sender captured";
        SenderStore.clear(context);
        try {
            // Post from a separate installed fixture process. Do not call the listener directly.
            post(flat, false);
            await("Real Android callback must capture sender", () -> flat.equals(MessagesListener.lastSender));
            assertTrue("Google source callback counted", MessagesListener.googleEvents > 0);
            assertTrue("Active snapshot finds current notification",
                MessagesListener.checkCurrentMessages().contains(flat));

            // A saved ignore rule must suppress the next actual Android callback.
            MessagesListener.setIgnored(context, key, true);
            int callbacks = MessagesListener.googleEvents;
            int captures = MessagesListener.count;
            MessagesListener.lastSender = "No sender captured";
            post("+1 202-555-0147", false);
            await("Ignored sender callback was delivered", () -> MessagesListener.googleEvents > callbacks);
            assertEquals("Ignored sender not captured", "No sender captured", MessagesListener.lastSender);
            assertEquals("Ignore does not increase captures", captures, MessagesListener.count);
            assertFalse("Snapshot also respects ignore rule",
                MessagesListener.checkCurrentMessages().startsWith("Recovered "));

            MessagesListener.setIgnored(context, key, false);
            post(flat, false);
            await("Undo allows actual callback again", () -> flat.equals(MessagesListener.lastSender));

            // Use Android MessagingStyle as SMS/RCS conversation notifications do.
            post(conversation, true);
            await("Conversation notification title captured",
                () -> conversation.equals(MessagesListener.lastSender));

            // Phone metadata survives when Messages shows a contact name instead of digits.
            Intent known = new Intent();
            known.setComponent(new ComponentName("com.google.android.apps.messaging",
                "com.tonyferlazzo.whostexting.fixture.SenderActivity"));
            known.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            known.putExtra("sender", "WT Known Contact");
            known.putExtra("phone", "+12025550198");
            known.putExtra("messaging_style", true);
            known.putExtra("seed_contact", true);
            context.startActivity(known);
            await("Known sender phone metadata captured", () ->
                "+12025550198".equals(MessagesListener.lastNumber));
            assertEquals("WT Known Contact", ContactActions.existingName(context, "+12025550198"));
            assertTrue(SenderStore.read(context).stream().anyMatch(row ->
                row.number.equals("+12025550198") && row.label.equals("WT Known Contact")));

            // Recover multiple existing notifications after missed live delivery.
            MessagesListener.setTesting(context, false);
            SenderStore.clear(context);
            Intent clear = new Intent().setComponent(new ComponentName("com.google.android.apps.messaging",
                "com.tonyferlazzo.whostexting.fixture.SenderActivity"));
            clear.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("clear", true);
            context.startActivity(clear);
            SystemClock.sleep(500);
            Intent older = new Intent().setComponent(clear.getComponent());
            older.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("sender", "(202) 555-0177")
                .putExtra("notification_id", 201);
            context.startActivity(older);
            SystemClock.sleep(500);
            Intent newer = new Intent().setComponent(clear.getComponent());
            newer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("sender", "(202) 555-0188")
                .putExtra("notification_id", 202);
            context.startActivity(newer);
            SystemClock.sleep(500);
            String recovered = MessagesListener.checkCurrentMessages();
            assertTrue("Snapshot must recover every current sender", recovered.startsWith("Recovered 2"));
            assertTrue("Older unknown sender retained", SenderStore.read(context).stream().anyMatch(row ->
                row.number.equals("+12025550177")));
            assertEquals("Newest sender remains first", "+12025550188", SenderStore.read(context).get(0).number);
            MessagesListener.setTesting(context, true);

            // Running state is retained in private settings rather than only RAM.
            assertTrue(context.getSharedPreferences("sender_preferences", Context.MODE_PRIVATE)
                .getBoolean("live_test_running", false));
            MessagesListener.setTesting(context, false);
            assertFalse(context.getSharedPreferences("sender_preferences", Context.MODE_PRIVATE)
                .getBoolean("live_test_running", true));
            int stoppedCaptures = MessagesListener.count;
            post("(202) 555-0199", false);
            SystemClock.sleep(1500);
            assertEquals("Stopped listener must not capture", stoppedCaptures, MessagesListener.count);
        } finally {
            MessagesListener.setIgnored(context, key, false);
            MessagesListener.setTesting(context, false);
            SenderStore.clear(context);
        }
    }
}
