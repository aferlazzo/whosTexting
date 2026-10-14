# Emulator-only notification fixture
This application is NOT Google Messages. It uses Google's package name only to exercise Who's Texting?'s existing source filter inside a clean AOSP emulator.
Never install this fixture on a real phone. It has no launcher and is not included in release artifacts.
The test posts synthetic flat and MessagingStyle notifications through Android's NotificationManager from a separate process. The instrumentation asserts real listener delivery, capture, ignore, undo, active snapshots, and stop-state behavior.
Passing these tests does not prove delivery on Tony's physical Pixel or verify Google's exact notification formats, SMS/RCS reception, or Android 17 policies.
