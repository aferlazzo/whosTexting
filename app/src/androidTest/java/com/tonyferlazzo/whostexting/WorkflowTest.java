package com.tonyferlazzo.whostexting;

import android.content.Context;
import android.content.Intent;
import android.provider.ContactsContract;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

public final class WorkflowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void recentSendersPersistDeduplicateAndStayBounded() {
        SenderStore.clear(context);
        try {
            for (int i = 0; i < 40; i++) SenderStore.record(context, "Sample " + i, "+1202555" + String.format("%04d", i));
            assertEquals(30, SenderStore.read(context).size());
            SenderStore.record(context, "Latest label", "+12025550039");
            assertEquals(30, SenderStore.read(context).size());
            assertEquals("Latest label", SenderStore.read(context).get(0).label);
            assertEquals("+12025550039", SenderStore.read(context).get(0).number);
        } finally { SenderStore.clear(context); }
    }

    @Test public void directoryResultsRequireExactNumberAndUsableEvidence() throws Exception {
        String key = "+12025550147";
        String wikidata = "{\"results\":{\"bindings\":["
            + "{\"itemLabel\":{\"value\":\"Sample Business\"},\"phone\":{\"value\":\"+1-202-555-0147\"},\"item\":{\"value\":\"http://www.wikidata.org/entity/Q123\"}},"
            + "{\"itemLabel\":{\"value\":\"Wrong Business\"},\"phone\":{\"value\":\"+1-202-555-0199\"},\"item\":{\"value\":\"http://www.wikidata.org/entity/Q456\"}}]}}";
        assertEquals(1, BusinessLookup.parseWikidata(wikidata, key).size());
        assertEquals("Sample Business", BusinessLookup.parseWikidata(wikidata, key).get(0).name);
        assertEquals("https://www.wikidata.org/wiki/Q123", BusinessLookup.parseWikidata(wikidata, key).get(0).evidence);
        String osm = "{\"elements\":[{\"id\":42,\"type\":\"node\",\"tags\":{\"name\":\"Another listing\",\"contact:phone\":\"(202) 555-0147\",\"website\":\"javascript:alert(1)\"}}]}";
        assertEquals(1, BusinessLookup.parseOsm(osm, key).size());
        assertEquals("", BusinessLookup.parseOsm(osm, key).get(0).website);
        BusinessLookup.Result conflict = BusinessLookup.lookup(key, url -> url.contains("wikidata") ? wikidata : osm);
        assertEquals("Conflicting listings must remain separate proposals", 2, conflict.matches.size());
        assertFalse(BusinessLookup.validName("Q123"));
        assertEquals("", BusinessLookup.safeWebsite("http://example.com"));
    }

    @Test public void failuresAndShortCodesNeverInventIdentity() {
        BusinessLookup.Result failed = BusinessLookup.lookup("+12025550147", url -> { throw new java.io.IOException(); });
        assertTrue(failed.matches.isEmpty()); assertEquals(2, failed.unavailable.size());
        BusinessLookup.Result empty = BusinessLookup.lookup("+12025550147", url ->
            url.contains("wikidata") ? "{\"results\":{\"bindings\":[]}}" : "{\"elements\":[]}");
        assertTrue(empty.matches.isEmpty()); assertTrue(empty.unavailable.isEmpty());
        final int[] calls = {0};
        BusinessLookup.lookup("12345", url -> { calls[0]++; return ""; });
        BusinessLookup.lookup("not-a-number", url -> { calls[0]++; return ""; });
        assertEquals("Short codes and malformed numbers are never sent externally", 0, calls[0]);
    }

    @Test public void savingRequiresApprovalAndOnlyPreparesContactsEditor() {
        String phone = "+12025550147";
        Intent intent = ContactActions.draft(phone, "Approved sample");
        assertEquals(Intent.ACTION_INSERT, intent.getAction());
        assertEquals("Approved sample", intent.getStringExtra(ContactsContract.Intents.Insert.NAME));
        assertEquals(phone, intent.getStringExtra(ContactsContract.Intents.Insert.PHONE));
        SenderStore.clear(context);
        SenderStore.record(context, phone, phone);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            captureScreen("review-screen.png");
            onView(withContentDescription("Name you approve for this number")).perform(replaceText("Approved sample"), closeSoftKeyboard());
            onView(withText("Review and save contact")).perform(scrollTo(), click());
            onView(withText("Approve this contact draft?")).check(matches(isDisplayed()));
            captureScreen("approval-screen.png");
            onView(withText("Cancel")).perform(click());
            // Canceling approval leaves the fictional number absent from Contacts.
            assertEquals("", ContactActions.existingName(context, phone));
        } finally { SenderStore.clear(context); }
    }

    private void captureScreen(String name) {
        android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("UI screenshot must be available", image);
        java.io.File directory = new java.io.File(context.getExternalFilesDir(null), "qa");
        directory.mkdirs();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { image.recycle(); }
    }

    @Test public void publicDirectorySmokeTest() {
        // Public Library of Congress number, independently corroborated at loc.gov/contact.
        // Never look up any user phone number in CI.
        BusinessLookup.Result result = BusinessLookup.lookup("+12027075000");
        assertTrue("Public reference lookup must find the documented organization; unavailable=" + result.unavailable,
            result.matches.stream().anyMatch(match -> match.name.equals("Library of Congress")));
    }
}
