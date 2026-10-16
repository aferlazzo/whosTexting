package com.tonyferlazzo.whostexting;

import android.app.Notification;
import android.content.Context;
import java.util.List;

/** A fictional input runs through the production parser and unknown-sender workflow.
 * Only Contacts, storage and external lookup are replaced by isolated test boundaries. */
final class WorkflowSelfTest {
    static final String NUMBER = "+12025550199";
    static final String SAMPLE = "This is Desert Dental. Your appointment is tomorrow.";
    static final class Result {
        boolean passed, alertPosted;
        String clues = "";
        String summary() {
            return (passed ? "Workflow test passed" : "Workflow test failed")
                + (alertPosted ? " · test alert submitted" : " · test alert blocked");
        }
        String detail() {
            return "SIMULATED TEST — fictional dental reminder.\n\n"
                + (clues.isEmpty() ? "No clue produced." : clues)
                + "\n\n" + (passed ? "PASS: sender extraction, unknown-sender processing, and message analysis."
                    : "FAIL: workflow checks did not match the expected result.")
                + "\n\n" + (alertPosted ? "Test alert sent to Android. Swipe down to look for WT TEST."
                    : "Test alert blocked. Use Enable alerts on the main screen.")
                + "\n\nContacts were simulated. Your Contacts and history were unchanged. No internet lookup."
                + "\n\nNot tested: delivery from Google Messages.";
        }
    }
    static Result run(Context context) {
        Result result = new Result();
        final int[] records = {0}, identifications = {0};
        Notification input = new Notification.Builder(context, "workflow_test_input")
            .setContentTitle(NUMBER).setContentText(SAMPLE).build();
        List<SenderWorkflow.Outcome> rows = SenderWorkflow.process(input, System.currentTimeMillis(), new SenderWorkflow.Environment() {
            public boolean canReadContacts() { return true; }
            public String contactName(String number) { return ""; }
            public boolean ignored(String label) { return false; }
            public void record(SenderStore.Sender sender, long time, String clues) { records[0]++; }
            public void identify(String number, String clues) {
                identifications[0]++;
                result.clues = clues;
                // The real Android notification builder and post operation are exercised.
                result.alertPosted = AutoIdentify.showTest(context, number, clues);
            }
        });
        result.passed = rows.size() == 1 && rows.get(0).sender.number.equals(NUMBER)
            && records[0] == 1 && identifications[0] == 1
            && result.clues.contains("Desert Dental") && result.clues.contains("Appointment")
            && result.clues.contains("not verified");
        context.getSharedPreferences("workflow_test", 0).edit()
            .putString("summary", result.summary()).putString("detail", result.detail())
            .putLong("time", System.currentTimeMillis()).apply();
        return result;
    }
    private WorkflowSelfTest() {}
}
