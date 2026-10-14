# Who's Texting?

Android notification-sender identification component for Google Messages on Pixel 6a.

## Recovery status — October 14, 2026

- Repository created and initial README pushed.
- Existing version **0.1.1** installed on the Pixel, but **notification access is disabled/greyed out**.
- Original development report: `WhosTexting-Milestone1.md` in the user's ChatGPT Library.
- Original source archive referenced in that report: `whos-texting-source.zip` (not yet recovered into this repository).
- Build record: https://github.com/aferlazzo/HelpMe/actions/runs/38082512911
- Original signed APK SHA-256: `b688a6652b575a6c7958b9c0a4c85f522a887ce6ff94f281b721ed5174412542`.
- The original report says Android lint, APK signature, parser tests, privacy checks, and an emulator fixture passed. **Real Google Messages SMS/RCS detection on the Pixel has not passed.**

## Scope of version 0.1.1

The original report describes a native Android notification listener with an optional read-only contacts lookup, status display, and a fresh-test action. It does **not** implement business identity lookup, writing contacts, or relabeling existing Google Messages threads. No message body collection is intended.

## Current blocker

The Android system's notification-access controls are greyed out on the Pixel. This may be Android's restricted-settings protection for a sideloaded APK; the root cause has not been verified. **Do not claim this is a manifest defect without inspecting the original source or APK.** Do not recommend bypassing Android security controls or disabling Play Protect globally.

## Recovery and verification plan

1. Recover the existing `whos-texting-source.zip` archive or original Android source from the earlier development session/build artifacts.
2. Inspect the Android manifest, notification listener service declaration, target SDK, app ID, and signing continuity.
3. Determine whether the installation source triggers Android's restricted-settings policy; use a supported installation/distribution method and explicit user consent.
4. Preserve the installed package identity and signing key for an in-place update where possible.
5. Verify listener permission and connection on the real Pixel; then test incoming SMS and RCS from an unfamiliar number without exposing message content.
6. Only after detection succeeds, implement evidence-based business identity suggestions and user-approved contact saving.

## Status

**Blocked on recovery of original source/APK and Android notification-access approval.** No new APK has been built or deployed from this repository.
