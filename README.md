# Who's Texting? — Android recovery

**Reconstructed Android source is now committed** after the original source archive could not be recovered. This is a fresh implementation of the notification-detection milestone, not a byte-for-byte restoration.

## Files
- `app/src/main/AndroidManifest.xml` — launcher and notification-listener declaration
- `app/src/main/java/com/tonyferlazzo/whostexting/MainActivity.java` — explicit permission explanation, Android settings shortcut, test status
- `app/src/main/java/com/tonyferlazzo/whostexting/MessagesListener.java` — only Google Messages sender titles, no message bodies
- `app/build.gradle` — Android app settings
- `.github/workflows/android-build.yml` — debug APK compilation and artifact

## Current status (October 14, 2026)
- Source recreation: **DONE**
- Source committed to this repository: **DONE**
- Automated build: **configured, not yet verified**
- Original package ID/signing certificate: **not recovered or verified**
- Compatible signed upgrade APK: **NOT produced**
- Pixel notification permission: **still blocked**
- Real Google Messages SMS/RCS test: **not run**

**Do not install the debug build as an update to the existing app.** The package ID and signing certificate have not been confirmed against the original APK. Android requires matching package identity and compatible signing for an in-place upgrade.

## Security and limitations
The prototype declares READ_CONTACTS and a notification listener. It does not declare Internet, SMS read, or contacts write permission. Android's notification listener access is broad, even though the code filters notifications by Google Messages package. No content from message bodies is intentionally inspected or stored. This reconstruction is only a detection test; business lookup, existing-message scanning, and saving contacts are not implemented.

## Original evidence
- Prior milestone: `WhosTexting-Milestone1.md` in ChatGPT Library
- Prior signed APK SHA-256: `b688a6652b575a6c7958b9c0a4c85f522a887ce6ff94f281b721ed5174412542`
- Prior build: https://github.com/aferlazzo/HelpMe/actions/runs/38082512911

## Next verification gates
1. Confirm the GitHub Actions build compiles successfully; fix any build errors.
2. Recover package name and signing fingerprint from the installed APK or previous build artifacts.
3. Build a properly signed update with the same identity, or explicitly choose a separate new installation.
4. Address Android's restricted-settings notification-access policy through supported permission flows; never bypass protections or disable Play Protect globally.
5. Test on the Pixel with real SMS and RCS notifications before adding identification and contact-saving features.
