package com.tonyferlazzo.whostexting;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

final class ContactActions {
    static boolean canRead(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }
    static String existingName(Context context, String number) {
        if (!canRead(context) || number.isEmpty()) return "";
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            String name = cursor != null && cursor.moveToFirst() ? cursor.getString(0) : "";
            return name == null ? "" : name;
        }
    }
    static Intent draft(String number, String approvedName) {
        String normalized = MessagesListener.numberKey(number);
        if (normalized.isEmpty() || approvedName.trim().isEmpty())
            throw new IllegalArgumentException("A number and approved name are required");
        // The Contacts editor owns the write. This application never writes contacts.
        return new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            .setType(ContactsContract.Contacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, approvedName.trim())
            .putExtra(ContactsContract.Intents.Insert.PHONE, normalized);
    }
}
