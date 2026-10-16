package com.tonyferlazzo.whostexting;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MessageCluesTest {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void cluesAreUsefulButNeverVerified() {
        String rx = MessageClues.analyze("Your Walgreens prescription is ready. Code 987654");
        assertTrue(rx.contains("Walgreens"));
        assertTrue(rx.contains("Prescription"));
        assertTrue(rx.contains("not verified"));
        assertFalse(rx.contains("987654"));
        assertTrue(MessageClues.analyze("Your verification code is 123456").contains("Verification"));
        assertTrue(MessageClues.analyze("Your appointment is tomorrow").contains("Appointment"));
        assertTrue(MessageClues.analyze("Your FedEx package arrives today").contains("Delivery"));
        assertTrue(MessageClues.analyze("Your invoice payment is due").contains("Payment"));
        assertTrue(MessageClues.analyze("Your order is ready for pickup").contains("pickup"));
        assertTrue(MessageClues.analyze("Here is a discount coupon").contains("Promotion"));
        assertEquals("", MessageClues.analyze(null));
        assertEquals("", MessageClues.analyze("See you tomorrow"));
        assertEquals("", MessageClues.analyze("upsides and chaseaways"));
        assertTrue(MessageClues.analyze("This is Desert Dental. Your appointment is Friday.").contains("Desert Dental"));
        assertTrue(MessageClues.analyze("Amazon delivery via UPS").contains("Multiple organizations"));
    }
    @Test public void notificationBodyMustBelongToTheNumber() {
        String number = "+12025550176";
        Notification flat = new Notification.Builder(context, "test")
            .setContentTitle(number).setContentText("Walgreens prescription ready").build();
        assertTrue(MessagesListener.messageClues(context, flat, number).contains("Walgreens"));
        assertEquals("", MessagesListener.messageClues(context, flat, "+12025550177"));
        Notification grouped = new Notification();
        grouped.flags |= Notification.FLAG_GROUP_SUMMARY;
        grouped.extras = new Bundle();
        grouped.extras.putCharSequence(Notification.EXTRA_TEXT, "Walgreens prescription ready");
        assertEquals("", MessagesListener.messageClues(context, grouped, number));
        Person person = new Person.Builder().setName("Unknown").setUri("tel:" + number).build();
        Bundle message = new Bundle();
        message.putParcelable("sender_person", person);
        message.putCharSequence("text", "FedEx package delivery");
        grouped.extras.putParcelableArray("android.messages", new Bundle[]{message});
        assertTrue(MessagesListener.messageClues(context, grouped, number).contains("FedEx"));
        assertEquals("", MessagesListener.messageClues(context, grouped, "+12025550177"));
        SenderStore.record(context, number, number, System.currentTimeMillis(), "Apparent purpose: Delivery");
        assertTrue(SenderStore.read(context).stream().anyMatch(row -> row.number.equals(number)
            && row.clues.equals("Apparent purpose: Delivery")));
        SenderStore.clear(context);
    }
}
