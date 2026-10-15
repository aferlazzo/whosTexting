# Who's Texting?

Android component for reviewing Google Messages notification senders. No SMS-app replacement.

## Version 1.0.0
- Monitoring starts automatically once notification access is granted, and its running/paused choice is retained locally.
- Captured sender labels and available phone metadata appear on the main review screen.
- Last 30 unique captured senders are kept locally, with Recent senders and explicit history erasure.
- Contacts check identifies existing saved numbers when READ_CONTACTS is allowed.
- Optional business lookup sends only the selected phone number, with explicit consent, to Wikidata and OpenStreetMap's Overpass API.
- Exact-number public directory matches are **UNVERIFIED proposals**, with evidence and listed-website links. Multiple matches stay separate; missing matches and unavailable services never invent names.
- User reviews a name and approves opening a contact draft. Android's Contacts editor owns the final Save and Google account choice. This app has no WRITE_CONTACTS permission.
- Ignore this number persists locally; Manage ignored numbers restores it.
- Main screen reports actual monitoring readiness. Diagnostics are separate.
- Persistent launcher icon and one permanent release signing certificate.

## Honest limits
The app does not read historical SMS/RCS texts, recover dismissed notifications, guarantee business lookup coverage, identify shared short codes, or authenticate a sender from a phone listing. Numbers can be reassigned or spoofed. Real Google Messages notification metadata varies across versions. Named senders with no phone metadata are shown as supplied labels and cannot be saved as inferred numbers.

Wikidata and OpenStreetMap are community directories. Their public data can be incomplete, stale, or conflicting; lookup is an attempt, not a universal reverse-phone service. No paid API or embedded secret is used.

## Privacy
Message bodies are never intentionally inspected or stored. No message text, contact list, or device location is transmitted. INTERNET is used only for consented directory lookups; Contacts access is read-only and optional. Sender history and ignore rules remain in private device settings; backup is disabled. Android notification-listener access is broad even though the application filters to Google Messages. No default SMS role is requested.

Data attribution: Wikidata (CC0); © OpenStreetMap contributors (ODbL). Source links remain visible beside proposals.

## Builds and tests
Release workflow validates the fixed signing fingerprint before publishing an APK. The private key is loaded from the encrypted WHOS_TEXTING_SIGNING_JSON repository secret and is never committed. Never distribute temporary debug APKs.

The notification integration workflow runs clean AOSP emulators (API 35 and 36). A clearly labeled, emulator-only fixture posts real Android notifications from a separate process. It uses the Messages package name solely to exercise the app's source filter and must never be installed on user phones. Tests cover callback delivery, flat and MessagingStyle sender labels, phone metadata, Contacts recognition, ignore/undo, snapshots, stop state, bounded persistence, directory mismatch/unknown/error cases, and contact-approval UI. A public-directory smoke test uses only the Library of Congress's published public number.

Emulator tests do not prove delivery from the real Google Messages app on Tony's physical Pixel. Report that distinction in every verification claim.
