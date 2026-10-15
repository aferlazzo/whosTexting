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
            String phone = getIntent().getStringExtra("phone");
            if (getIntent().getBooleanExtra("seed_contact", false) && phone != null) {
                android.content.ContentValues raw = new android.content.ContentValues();
                android.net.Uri contact = getContentResolver().insert(android.provider.ContactsContract.RawContacts.CONTENT_URI, raw);
                if (contact != null) {
                    long id = android.content.ContentUris.parseId(contact);
                    android.content.ContentValues nameRow = new android.content.ContentValues();
                    nameRow.put(android.provider.ContactsContract.Data.RAW_CONTACT_ID, id);
                    nameRow.put(android.provider.ContactsContract.Data.MIMETYPE,
                        android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                    nameRow.put(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "WT Known Contact");
                    getContentResolver().insert(android.provider.ContactsContract.Data.CONTENT_URI, nameRow);
                    android.content.ContentValues phoneRow = new android.content.ContentValues();
                    phoneRow.put(android.provider.ContactsContract.Data.RAW_CONTACT_ID, id);
                    phoneRow.put(android.provider.ContactsContract.Data.MIMETYPE,
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                    phoneRow.put(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, phone);
                    getContentResolver().insert(android.provider.ContactsContract.Data.CONTENT_URI, phoneRow);
                }
            }
            Notification.Builder builder = new Notification.Builder(this, "fixture")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentTitle(sender)
                .setContentText("Synthetic emulator test body");
            if (getIntent().getBooleanExtra("messaging_style", false)) {
                Person recipient = new Person.Builder().setName("Fixture recipient").build();
                Person from = new Person.Builder().setName(sender).setUri(phone == null ? null : "tel:" + phone).build();
                builder.setStyle(new Notification.MessagingStyle(recipient)
                    .addMessage("Synthetic emulator test body", System.currentTimeMillis(), from));
            }
            manager.notify(101, builder.build());
        }
        finish();
    }
}
