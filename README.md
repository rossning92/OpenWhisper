# Speech To Text

Android floating dictation app. Transcribes English on device using Parakeet via transcribe.cpp; audio stays on the device. Recordings are limited to 5 minutes.

- Requirements: Android 8.0+ and an ARM64 CPU with FP16 SIMD and dot-product support.
- Setup: open the app, grant microphone and overlay permissions, then download the Offline English model (731 MB) in Settings. Internet is needed for the download; transcription works offline afterward.
- Use: focus a text field, tap the floating button to record, then tap Send to transcribe. Enable the app's Accessibility Service in Settings to insert text; otherwise results are copied to the clipboard. Tap X to cancel; drag the control to move it.
- Build from the `myscripts` root: `run_script repos/SpeechToText/build.sh`. Uses JDK 21, Android SDK 34, NDK 28.2.13676358 and CMake 3.31.6. The first build downloads dependencies. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- On Android/Termux, build with `CWD="/path/to/SpeechToText" run_script r/android/gradle.sh assembleDebug`. Deploy from the `myscripts` root with `run_script repos/SpeechToText/deploy.sh`; this builds, installs, grants permissions, enables accessibility and opens the app.
- Optional Termux action: the terminal button sends the transcription to `~/myscripts/bin/handle_transcription.sh` in Termux. Requires Termux, that script, and permission to run Termux commands.
