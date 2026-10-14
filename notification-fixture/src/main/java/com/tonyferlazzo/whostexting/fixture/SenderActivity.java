package com.tonyferlazzo.whostexting.fixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.os.Bundle;

// Only synthetic notification data. No networking, SMS or contacts permissions.
public final class SenderActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            "fixture", "Emulator fixture", NotificationManager.IMPORTANCE_DEFAULT));
        if (getIntent().getBooleanExtra("clear", false)) {
            manager.cancelAll();
        } else {
            String sender = getIntent().getStringExtra("sender");
            if (sender == null) sender = "(202) 555-0147";
            Notification.Builder builder = new Notification.Builder(this, "fixture")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentTitle(sender)
                .setContentText("Synthetic emulator test body");
            if (getIntent().getBooleanExtra("messaging_style", false)) {
                Person recipient = new Person.Builder().setName("Fixture recipient").build();
                Person from = new Person.Builder().setName(sender).build();
                builder.setStyle(new Notification.MessagingStyle(recipient)
                    .addMessage("Synthetic emulator test body", System.currentTimeMillis(), from));
            }
            manager.notify(101, builder.build());
        }
        finish();
    }
}
