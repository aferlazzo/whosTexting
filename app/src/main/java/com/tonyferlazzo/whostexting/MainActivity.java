package com.tonyferlazzo.whostexting;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView monitor, senderText, identityText, lookupText;
    private Button accessButton, lookupButton, saveButton, ignoreButton, sourceButton, websiteButton, pauseButton;
    private EditText nameInput;
    private SenderStore.Sender selected;
    private BusinessLookup.Match proposal;
    private boolean busy;
    private String knownName = "";
    private final Runnable updater = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!getSharedPreferences("sender_preferences", MODE_PRIVATE).contains("live_test_running"))
            MessagesListener.setTesting(this, true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding, padding + insets.getSystemWindowInsetTop(), padding,
                padding + insets.getSystemWindowInsetBottom()); return insets;
        });
        String version = "1.0.0";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { }
        TextView heading = text(root, "Who's Texting? " + version); heading.setTextSize(24);
        monitor = text(root, "Checking notification access…");
        accessButton = button(root, "Enable notification access", this::enableAccess);
        senderText = text(root, "No sender captured yet."); senderText.setTextSize(22);
        identityText = text(root, "");
        button(root, "Check Contacts", () -> {
            if (!ContactActions.canRead(this)) requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 10);
            else updateContact();
        });
        lookupButton = button(root, "Look up business", this::askLookup);
        lookupText = text(root, "Business lookup is optional. No contact is saved automatically.");
        sourceButton = button(root, "View directory evidence", () -> openWeb(proposal == null ? "" : proposal.evidence));
        websiteButton = button(root, "View listed website", () -> openWeb(proposal == null ? "" : proposal.website));
        nameInput = new EditText(this);
        nameInput.setSingleLine(true); nameInput.setTextSize(18);
        nameInput.setHint("Name you approve for this number");
        nameInput.setContentDescription("Name you approve for this number");
        root.addView(nameInput);
        saveButton = button(root, "Review and save contact", this::reviewSave);
        ignoreButton = button(root, "Ignore this number", this::ignore);
        button(root, "Recent senders", this::showRecent);
        button(root, "Manage ignored numbers", this::manageIgnored);
        pauseButton = button(root, "Pause monitoring", () -> {
            MessagesListener.setTesting(this, !MessagesListener.testing); refresh();
        });
        button(root, "Diagnostics and privacy", this::showDiagnostics);
        text(root, "© Anthony Ferlazzo, 2026").setTextSize(12);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(android.graphics.Color.WHITE);
        scroll.addView(root); setContentView(scroll);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            getWindow().getInsetsController().setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        updateButtons(); refresh();
    }
    private TextView text(LinearLayout parent, String value) {
        TextView view = new TextView(this); view.setTextSize(18); view.setTextColor(android.graphics.Color.rgb(27, 52, 64)); view.setText(value);
        view.setPadding(0, 10, 0, 10); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false);
        button.setOnClickListener(view -> action.run()); parent.addView(button); return button;
    }
    private boolean hasAccess() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(this, MessagesListener.class));
    }
    @Override protected void onResume() {
        super.onResume();
        if (hasAccess() && MessagesListener.connected && MessagesListener.testing) MessagesListener.checkCurrentMessages();
        handler.removeCallbacks(updater); updater.run(); updateContact();
    }
    @Override protected void onPause() { handler.removeCallbacks(updater); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(updater); worker.shutdownNow(); super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); updateContact(); refresh();
    }
    private void refresh() {
        if (monitor == null) return;
        boolean allowed = hasAccess();
        monitor.setText(!allowed ? "Monitoring off — notification access needs your approval."
            : !MessagesListener.connected ? "Permission allowed — waiting for Android to connect."
            : MessagesListener.testing ? "Monitoring on — you can leave this screen."
            : "Monitoring paused.");
        accessButton.setVisibility(allowed ? View.GONE : View.VISIBLE);
        pauseButton.setText(MessagesListener.testing ? "Pause monitoring" : "Resume monitoring");
        List<SenderStore.Sender> recent = SenderStore.read(this);
        if (!busy && nameInput.getText().toString().trim().isEmpty() && proposal == null) {
            for (SenderStore.Sender candidate : recent) {
                if (!MessagesListener.isIgnored(this, candidate.number)) {
                    if (selected == null || !selected.id.equals(candidate.id)) select(candidate);
                    break;
                }
            }
        }
        updateButtons();
    }
    private void select(SenderStore.Sender sender) {
        selected = sender; proposal = null; nameInput.setText("");
        senderText.setText(sender.label + (sender.number.isEmpty() || sender.label.equals(sender.number)
            ? "" : "\n" + sender.number));
        lookupText.setText("No business identity checked yet. Lookup results are proposals, not verified senders.");
        updateContact(); updateButtons();
    }
    private void updateContact() {
        if (selected == null || identityText == null) return;
        knownName = "";
        try { knownName = ContactActions.existingName(this, selected.number); }
        catch (RuntimeException error) { identityText.setText("Contacts check unavailable."); return; }
        identityText.setText(!knownName.isEmpty() ? "Already in Contacts: " + knownName
            : selected.number.isEmpty() ? "Google Messages supplied this label, but no phone number. No identity inferred."
            : ContactActions.canRead(this) ? "Not found in Contacts."
            : "Contacts haven't been checked. Check Contacts to allow a local lookup.");
        updateButtons();
    }
    private void updateButtons() {
        if (lookupButton == null || nameInput == null) return;
        boolean hasNumber = selected != null && !selected.number.isEmpty();
        boolean ignored = hasNumber && MessagesListener.isIgnored(this, selected.number);
        lookupButton.setEnabled(hasNumber && knownName.isEmpty() && !ignored && !busy);
        saveButton.setEnabled(hasNumber && knownName.isEmpty() && !ignored && !busy);
        ignoreButton.setEnabled(hasNumber && !ignored && !busy);
        nameInput.setEnabled(hasNumber && knownName.isEmpty() && !ignored && !busy);
        sourceButton.setVisibility(proposal == null ? View.GONE : View.VISIBLE);
        websiteButton.setVisibility(proposal == null || proposal.website.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void enableAccess() {
        new AlertDialog.Builder(this).setTitle("Allow sender detection")
            .setMessage("Android grants broad notification access. Who's Texting? only inspects Google Messages sender labels and phone metadata; it never reads message bodies.\n\nIf Android shows Controlled by Restricted Setting, open this app's App info from its home-screen icon, then ⋮ → Allow restricted settings. This differs from the app's Notifications setting for sending notifications.")
            .setNegativeButton("Cancel", null).setPositiveButton("Open permission screen", (dialog, which) ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).show();
    }
    private void askLookup() {
        if (selected == null) return;
        final SenderStore.Sender target = selected;
        if (target.number.replaceAll("[^0-9]", "").length() < 7) {
            lookupText.setText("This is a short code. Short codes can be shared and cannot be identified reliably by this lookup. You can ignore it or supply a name you already know."); return;
        }
        new AlertDialog.Builder(this).setTitle("Look up this number?")
            .setMessage("Send only " + target.number + " to Wikidata and OpenStreetMap's Overpass service to look for a public listing?\n\nNo message text, contact list, or location is sent. Their directory coverage is limited. A matching listing does not authenticate who sent the text.")
            .setNegativeButton("Cancel", null).setPositiveButton("Look up", (dialog, which) -> lookup(target)).show();
    }
    private void lookup(SenderStore.Sender target) {
        busy = true; lookupText.setText("Checking public phone listings…"); updateButtons();
        worker.execute(() -> {
            BusinessLookup.Result result = BusinessLookup.lookup(target.number);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false; updateButtons();
                if (selected == null || !selected.id.equals(target.id)) return;
                if (result.matches.isEmpty()) {
                    lookupText.setText(result.unavailable.isEmpty()
                        ? "No exact public-directory match found. Identity remains unknown. No name was invented."
                        : "No match found in the sources that responded. Unavailable: " + String.join(", ", result.unavailable) + ". Identity remains unknown."); return;
                }
                if (result.matches.size() == 1) useProposal(result.matches.get(0));
                else {
                    String[] choices = new String[result.matches.size()];
                    for (int i = 0; i < choices.length; i++) choices[i] = result.matches.get(i).name + " — " + result.matches.get(i).source;
                    lookupText.setText("Multiple public listings match. They may conflict. None is a verified sender.");
                    new AlertDialog.Builder(this).setTitle("Choose a listing to review")
                        .setItems(choices, (dialog, which) -> useProposal(result.matches.get(which)))
                        .setNegativeButton("Cancel", null).show();
                }
            });
        });
    }
    private void useProposal(BusinessLookup.Match match) {
        proposal = match; nameInput.setText(match.name);
        lookupText.setText("UNVERIFIED directory proposal: " + match.name + "\nSource: " + match.source
            + "\nExact phone match; this does not prove who sent the text. Review the evidence before saving.");
        updateButtons();
    }
    private void reviewSave() {
        if (selected == null) return;
        final String number = selected.number, name = nameInput.getText().toString().trim();
        if (name.isEmpty()) { lookupText.setText("Enter the name you approve, or choose a directory proposal first."); return; }
        try {
            String existing = ContactActions.existingName(this, number);
            if (!existing.isEmpty()) { identityText.setText("Already in Contacts: " + existing + ". No duplicate draft opened."); return; }
        } catch (RuntimeException error) { lookupText.setText("Contacts check unavailable. Verify duplicates in the Contacts editor."); }
        new AlertDialog.Builder(this).setTitle("Approve this contact draft?")
            .setMessage(name + "\n" + number + "\n\n" + (proposal == null ? "This is a name you supplied." : "The directory proposal is unverified.")
                + "\nThe Contacts editor opens next. Choose your Google account there and press Save only when satisfied. Nothing is written automatically.")
            .setNegativeButton("Cancel", null).setPositiveButton("Open contact draft", (dialog, which) -> {
                try { startActivity(ContactActions.draft(number, name)); }
                catch (RuntimeException error) { lookupText.setText("No Contacts editor is available. No contact was saved."); }
            }).show();
    }
    private void ignore() {
        if (selected == null) return;
        MessagesListener.setIgnored(this, selected.number, true);
        lookupText.setText("Ignored. Texts still arrive in Google Messages; sender suggestions from this number are skipped.");
        proposal = null; nameInput.setText(""); updateButtons();
    }
    private void showRecent() {
        List<SenderStore.Sender> recent = SenderStore.read(this);
        if (recent.isEmpty()) { lookupText.setText("No senders captured here yet. This app does not read historical texts."); return; }
        String[] labels = new String[recent.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = recent.get(i).label
            + (recent.get(i).number.isEmpty() ? "" : " · " + recent.get(i).number);
        new AlertDialog.Builder(this).setTitle("Recent captured senders")
            .setItems(labels, (dialog, which) -> select(recent.get(which))).setNegativeButton("Close", null).show();
    }
    private void manageIgnored() {
        String[] numbers = MessagesListener.ignoredNumbers(this).toArray(new String[0]);
        if (numbers.length == 0) { lookupText.setText("No numbers are ignored."); return; }
        java.util.Arrays.sort(numbers);
        new AlertDialog.Builder(this).setTitle("Tap a number to stop ignoring it")
            .setItems(numbers, (dialog, which) -> {
                MessagesListener.setIgnored(this, numbers[which], false);
                lookupText.setText("Restored: " + numbers[which]); updateButtons();
            }).setNegativeButton("Close", null).show();
    }
    private void openWeb(String url) {
        if (url.isEmpty()) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (RuntimeException error) { lookupText.setText("No browser is available to open the evidence."); }
    }
    private void showDiagnostics() {
        new AlertDialog.Builder(this).setTitle("Diagnostics and privacy")
            .setMessage("Notification access: " + (hasAccess() ? "Allowed" : "Not allowed")
                + "\nListener connected: " + MessagesListener.connected
                + "\nMonitoring: " + (MessagesListener.testing ? "Running" : "Paused")
                + "\nGoogle Messages callbacks: " + MessagesListener.googleEvents
                + "\nDiagnostic: " + MessagesListener.diagnostic
                + "\n\nOnly the latest 30 unique sender labels/numbers are kept privately on this phone. Message bodies are not read or stored. No default SMS role or contacts write permission is requested. External lookups happen only after you approve sending the selected number.\n\nDirectory data: Wikidata (CC0) and © OpenStreetMap contributors (ODbL). Public listings may be outdated or shared; no listing authenticates a message sender.")
            .setPositiveButton("Close", null)
            .setNeutralButton("Check current notifications", (dialog, which) -> {
                lookupText.setText(MessagesListener.checkCurrentMessages()); refresh();
            }).setNegativeButton("Erase sender history", (dialog, which) ->
                new AlertDialog.Builder(this).setTitle("Erase local sender history?")
                    .setMessage("This removes the captured labels and numbers, not your texts or Contacts.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Erase", (confirm, button) -> {
                        SenderStore.clear(this); selected = null; proposal = null; knownName = "";
                        MessagesListener.lastSender = "No sender captured"; MessagesListener.lastNumber = "";
                        nameInput.setText(""); senderText.setText("Sender history erased.");
                        identityText.setText(""); lookupText.setText(""); updateButtons();
                    }).show()).show();
    }
}
