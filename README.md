# Who's Texting?

Android component for reviewing Google Messages notification senders. No SMS-app replacement.

## Version 1.0.6
- Test unknown sender uses the same sender parser and processing path as live notifications, with fictional input, simulated unknown Contacts, isolated history and no external lookup.
- The test submits a clearly labeled WT TEST alert through the same Android notification builder/post code used by live alerts. Missing permission or blocked test channels are reported, not treated as successful alert delivery.
- Test results persist separately from real sender history. No test edits Contacts, replaces Google Messages, sends an SMS, or changes monitoring.
- System bar insets now protect the scrolling viewport, so lower controls cannot scroll underneath Android navigation buttons. The keyboard opens only when requested.
- Main screen separates monitoring, identification permissions, latest real capture, and the last simulated test. Contacts and alert setup actions appear only when needed.
- Current-notification check and Recent senders are prominent. Optional directory lookup, pause, ignored numbers, and diagnostics sit under Settings and privacy.
- Known-contact and missing/failed Contacts queries cannot proceed to body analysis or automatic lookup. Rechecking an unchanged notification does not repeatedly alert.
- API 35/36 tests exercise the shared workflow, known/unknown/ignored/permission-failure branches, real test alert submission, and UI repeatability with unchanged real history/settings.

## Previous version 1.0.5
- A visible Test sample message button runs the real local analyzer immediately, without waiting for a text or typing.
- A labeled fictional sample shows the extracted result and four pass/fail checks. It does not change history, Contacts, external-lookup consent, or monitoring.
- The result explicitly distinguishes local analysis from Google Messages notification delivery.
- The actual button and dialog are exercised on API 35 and 36 before release.

## Previous version 1.0.4
- Message-first identification analyzes available notification text locally for unknown numbers (including short codes).
- Conservative rules extract apparent purpose and mentioned organizations, clearly marked as unverified message clues. No remote AI or API key is used.
- Useful clues appear in sender review and an identification notification. Directory lookup is the fallback when automatic external lookup is enabled and no useful clues exist.
- Raw text is neither stored nor transmitted. Derived clues remain in the existing erasable 30-sender history.
- Contacts permission is required; saved contacts and ambiguous message-to-sender associations are skipped.

## Previous version 1.0.3
- Explicit opt-in enables automatic business-directory lookup for newly detected unknown numbers.
- Automatic lookup pauses without Contacts permission and skips saved contacts, including contacts without a display name. Failed contact queries stop the lookup.
- Android notification permission is requested after Contacts permission. The main screen shows missing Contacts permission or disabled alerts.
- Identification notifications label proposals as unverified, distinguish incomplete directory lookups from no matches, and open the captured sender for review.
- Release publication requires passing API 35 and 36 emulator tests and verification of the existing permanent signing certificate.

## Existing features
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
Available message notification bodies are inspected locally to derive unverified sender/purpose clues; raw bodies are never stored. No message text, contact list, or device location is transmitted. INTERNET is used only for consented directory lookups; Contacts access is read-only and optional. Sender history and ignore rules remain in private device settings; backup is disabled. Android notification-listener access is broad even though the application filters to Google Messages. No default SMS role is requested.

Data attribution: Wikidata (CC0); © OpenStreetMap contributors (ODbL). Source links remain visible beside proposals.

## Builds and tests
Release workflow validates the fixed signing fingerprint before publishing an APK. The private key is loaded from the encrypted WHOS_TEXTING_SIGNING_JSON repository secret and is never committed. Never distribute temporary debug APKs.

The notification integration workflow runs clean AOSP emulators (API 35 and 36). A clearly labeled, emulator-only fixture posts real Android notifications from a separate process. It uses the Messages package name solely to exercise the app's source filter and must never be installed on user phones. Tests cover callback delivery, flat and MessagingStyle sender labels, phone metadata, Contacts recognition, ignore/undo, snapshots, stop state, bounded persistence, directory mismatch/unknown/error cases, and contact-approval UI. A public-directory smoke test uses only the Library of Congress's published public number.

Emulator tests do not prove delivery from the real Google Messages app on Tony's physical Pixel. Report that distinction in every verification claim.

## Development and acceptance rules
- Run the automated notification and UI suites before publishing a signed update. Preserve the permanent certificate.
- Give the user one installation route: the public GitHub release page and its APK asset. Do not replace that route with experimental file URLs.
- Distinguish compiled, emulator-tested, installed, simulated-on-device-tested, and real-Google-Messages-captured. Never turn one into a claim of the others.
- On-device tests must need no helper, contact edits, fictional contact saves, or waiting for strangers. Label simulated inputs, mock boundaries and remaining limits.
- Keep normal monitoring and the last real capture visible. Show the next missing permission directly, and keep diagnostics out of routine use.
- Physical Pixel checks reported by Tony: 1.0.5 installed, local sample analysis passed, and Fran's real incoming notification appeared in Recent senders. Real unknown-sender Google Messages delivery has not been independently observed.
