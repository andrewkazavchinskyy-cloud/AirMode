# AirMode
Original Kotlin implementation; never copy companion app source. Follow the portable contract at https://github.com/andrewkazavchinskyy-cloud/corp.
Use native Android Bluetooth and a single repository StateFlow. Never confirm a mode from a socket write. Require a protocol-confirmed supported ANC model before noise writes. No INTERNET permission or runtime network. Preserve all Git history.
Verify with ./gradlew testDebugUnitTest lintRelease assembleRelease. Physical AirPods qualification remains required; record actual evidence in docs/VERIFICATION.md.
