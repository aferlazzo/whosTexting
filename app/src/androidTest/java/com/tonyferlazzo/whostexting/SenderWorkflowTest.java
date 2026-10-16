package com.tonyferlazzo.whostexting;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SenderWorkflowTest {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    static final class Fake implements SenderWorkflow.Environment {
        boolean permission = true, ignored, failContacts;
        String known = "", clues = "";
        int records, lookups;
        public boolean canReadContacts() { return permission; }
        public String contactName(String number) {
            if (failContacts) throw new IllegalStateException("Contacts unavailable");
            return known;
        }
        public boolean ignored(String number) { return ignored; }
        public void record(SenderStore.Sender sender, long time, String textClues) { records++; clues = textClues; }
        public void identify(String number, String textClues) { lookups++; clues = textClues; }
    }
    Notification message(String body) {
        return new Notification.Builder(context, "fixture").setContentTitle(WorkflowSelfTest.NUMBER)
            .setContentText(body).build();
    }
    @Test public void sharedWorkflowExtractsUnknownButProtectsKnownIgnoredAndUnavailableContacts() {
        Notification n = message(WorkflowSelfTest.SAMPLE);
        Fake unknown = new Fake();
        assertEquals(1, SenderWorkflow.process(n, 1, unknown).size());
        assertEquals(1, unknown.records); assertEquals(1, unknown.lookups);
        assertTrue(unknown.clues.contains("Desert Dental"));
        Fake known = new Fake(); known.known = "Fran";
        SenderWorkflow.process(n, 1, known);
        assertEquals(1, known.records); assertEquals(0, known.lookups); assertEquals("", known.clues);
        Fake denied = new Fake(); denied.permission = false;
        assertEquals("Contacts permission needed", SenderWorkflow.process(n, 1, denied).get(0).status);
        assertEquals(0, denied.lookups); assertEquals("", denied.clues);
        Fake failed = new Fake(); failed.failContacts = true;
        assertEquals("Contacts check unavailable", SenderWorkflow.process(n, 1, failed).get(0).status);
        assertEquals(0, failed.lookups);
        Fake ignored = new Fake(); ignored.ignored = true;
        assertTrue(SenderWorkflow.process(n, 1, ignored).isEmpty());
        assertEquals(0, ignored.records); assertEquals(0, ignored.lookups);
    }
    @Test public void noCluesFallsBackAndEmptyNotificationCannotInventSender() {
        Fake fake = new Fake();
        SenderWorkflow.process(message("See you tomorrow"), 1, fake);
        assertEquals(1, fake.lookups); assertEquals("", fake.clues);
        assertTrue(SenderWorkflow.process(null, 1, new Fake()).isEmpty());
    }
    @Test public void selfTestReportsBlockedAlertsHonestlyAndDoesNotAlterRealData() {
        String before = context.getSharedPreferences("captured_senders", 0).getString("recent", "[]");
        boolean consent = AutoIdentify.enabled(context), monitoring = MessagesListener.testing;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(AutoIdentify.TEST_ID);
        Context denied = new ContextWrapper(context) {
            @Override public int checkSelfPermission(String permission) {
                return Manifest.permission.POST_NOTIFICATIONS.equals(permission)
                    ? PackageManager.PERMISSION_DENIED : super.checkSelfPermission(permission);
            }
        };
        WorkflowSelfTest.Result result = WorkflowSelfTest.run(denied);
        assertTrue(result.passed); assertFalse(result.alertPosted);
        assertTrue(result.detail().contains("Test alert blocked"));
        assertTrue(result.detail().contains("Not tested: delivery from Google Messages"));
        assertEquals(before, context.getSharedPreferences("captured_senders", 0).getString("recent", "[]"));
        assertEquals(consent, AutoIdentify.enabled(context)); assertEquals(monitoring, MessagesListener.testing);
        assertFalse(java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n -> n.getId() == AutoIdentify.TEST_ID));
    }
}
