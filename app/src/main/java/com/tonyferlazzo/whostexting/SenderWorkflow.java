package com.tonyferlazzo.whostexting;

import android.app.Notification;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** One sender-processing path, used by live notifications and the isolated on-phone test. */
final class SenderWorkflow {
    interface Environment {
        boolean canReadContacts();
        String contactName(String number);
        boolean ignored(String label);
        void record(SenderStore.Sender sender, long time, String clues);
        void identify(String number, String clues);
    }
    static final class Outcome {
        final SenderStore.Sender sender;
        final String clues, status;
        Outcome(SenderStore.Sender sender, String clues, String status) {
            this.sender = sender; this.clues = clues; this.status = status;
        }
    }
    static List<Outcome> process(Notification notification, long time, Environment env) {
        List<Outcome> outcomes = new ArrayList<>();
        for (SenderStore.Sender row : MessagesListener.notificationSenders(notification)) {
            if (env.ignored(row.number) || env.ignored(row.label)) continue;
            String clues = "", status = "No phone number supplied";
            boolean unknown = false;
            if (!row.number.isEmpty()) {
                if (!env.canReadContacts()) status = "Contacts permission needed";
                else try {
                    String known = env.contactName(row.number);
                    if (!known.isEmpty()) status = "Known contact — no external lookup";
                    else {
                        unknown = true;
                        clues = MessagesListener.notificationClues(notification, row.number);
                        status = clues.isEmpty() ? "No useful message clues" : "Unverified message clues found";
                    }
                } catch (RuntimeException unavailable) { status = "Contacts check unavailable"; }
            }
            env.record(row, time, clues);
            if (unknown) env.identify(row.number, clues);
            outcomes.add(new Outcome(row, clues, status));
        }
        return outcomes;
    }
    static List<Outcome> live(Context context, Notification notification, long time) {
        List<SenderStore.Sender> previous = SenderStore.read(context);
        return process(notification, time, new Environment() {
            public boolean canReadContacts() { return ContactActions.canRead(context); }
            public String contactName(String number) { return ContactActions.existingName(context, number); }
            public boolean ignored(String label) { return MessagesListener.isIgnored(context, label); }
            public void record(SenderStore.Sender sender, long timestamp, String clues) {
                SenderStore.record(context, sender.label, sender.number, timestamp, clues);
            }
            public void identify(String number, String clues) {
                for (SenderStore.Sender row : previous) if (row.number.equals(number)
                    && (row.time > time || (row.time == time && row.clues.equals(clues)))) return;
                AutoIdentify.submitWithClues(context, number, clues);
            }
        });
    }
    private SenderWorkflow() {}
}
