package com.tonyferlazzo.whostexting;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import java.util.List;
import static org.junit.Assert.*;

final class NotificationBuilderTests {
    static void verify(Context context) {
        Notification.Builder builder = new Notification.Builder(context, "fixture")
            .setSmallIcon(android.R.drawable.sym_action_chat).setGroup("fixture").setGroupSummary(true)
            .setContentTitle("Messages");
        Person recipient = new Person.Builder().setName("Local recipient").build();
        Person first = new Person.Builder().setName("(202) 555-0147").build();
        Person second = new Person.Builder().setName("Known fixture sender").setUri("tel:+12025550188").build();
        builder.setStyle(new Notification.MessagingStyle(recipient)
            .addMessage("Private synthetic body 1", 1, first)
            .addMessage("Private synthetic body 2", 2, second));
        List<SenderStore.Sender> metadata = MessagesListener.notificationSenders(builder.build());
        assertEquals("Both explicit sender metadata entries recovered", 2, metadata.size());
        assertTrue(metadata.stream().anyMatch(row -> row.number.equals("+12025550147")));
        assertTrue(metadata.stream().anyMatch(row -> row.number.equals("+12025550188")));
        assertFalse(metadata.stream().anyMatch(row -> row.label.contains("Private") || row.label.equals("Messages")));

        String label = "(202) 555-0166";
        SpannableString line = new SpannableString(label + " Body contains (202) 555-0199");
        line.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int bodyNumber = line.toString().indexOf("(202) 555-0199");
        line.setSpan(new StyleSpan(Typeface.BOLD), bodyNumber, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setStyle(new Notification.InboxStyle().addLine(line));
        List<SenderStore.Sender> inbox = MessagesListener.notificationSenders(builder.build());
        assertEquals("Only the leading sender-number span is used", 1, inbox.size());
        assertEquals("+12025550166", inbox.get(0).number);
        assertFalse(inbox.get(0).label.contains("Body"));

        builder.setStyle(new Notification.InboxStyle().addLine("Plain body mentions (202) 555-0199"));
        assertTrue("Unstructured summary text must not be treated as a sender",
            MessagesListener.notificationSenders(builder.build()).isEmpty());
    }
}
