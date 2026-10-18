# Speech To Text

Android floating dictation app. Transcribes on device using Parakeet or Qwen3-ASR via transcribe.cpp; audio stays on the device. Recordings are limited to 5 minutes.

- Requirements: Android 8.0+ and an ARM64 CPU with FP16 SIMD and dot-product support.
- Setup: open the app, grant microphone and overlay permissions, choose Parakeet (English, 731 MB) or Qwen3-ASR (multilingual, 850 MB), then download it. Internet is needed for the download; transcription works offline afterward.
- Custom dictionary: in Settings, enter one word or phrase per line, most important first. Qwen3-ASR uses these as hints; Parakeet ignores them. Blank lines and duplicates are ignored. Clear the list to disable it. Long lists may be shortened to fit the model's context.
- Use: focus a text field, tap the floating button to record, then tap Send to transcribe. Enable Accessibility Service from the app's main screen to insert text; otherwise results are copied to the clipboard. Tap X to cancel; drag the control to move it.
- Auto-finish: enable "Auto-finish after speech" in Settings to send after 1.5 seconds of silence following speech. Off by default; changes apply to the next recording. Uses on-device Silero VAD 6.2 (https://github.com/snakers4/silero-vad), bundled with the app, with no extra model download. Send and X still work manually.
- Build from the `myscripts` root: `run_script repos/SpeechToText/build.sh`. Uses JDK 21, Android SDK 34, NDK 28.2.13676358 and CMake 3.31.6. The first build downloads dependencies. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- On Android/Termux, build with `CWD="/path/to/SpeechToText" run_script r/android/gradle.sh assembleDebug`. Deploy from the `myscripts` root with `run_script repos/SpeechToText/deploy.sh`; this builds, installs, grants permissions, enables accessibility and opens the app.
- When no input field is focused: choose "Copy to clipboard" (default) or "Run Termux command (terminal button)" in Settings. Also applies when accessibility is unavailable. Changes take effect immediately.
- Optional Termux action: the terminal button sends the transcription to `~/myscripts/bin/handle_transcription.sh` in Termux. Requires Termux, that script, and permission to run Termux commands.

## Releases

Push a tag such as `v1.0.1` to build a signed ARM64 APK and publish it with SHA-256 checksums on GitHub Releases. Tags must be `vMAJOR.MINOR.PATCH` with components 0-999; versionCode is `MAJOR * 1000000 + MINOR * 1000 + PATCH` and must increase for updates.

GitHub Actions requires repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. Keep a backup of the keystore and passwords: future updates must use the same key. The local backup is in ignored `.signing/`.

For local releases, set `RELEASE_STORE_FILE` (absolute keystore path), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_VERSION_CODE`, and `RELEASE_VERSION_NAME`, then run `run_script repos/SpeechToText/build.sh assembleRelease` from the myscripts root. Output: `app/build/outputs/apk/release/app-release.apk`. Missing signing credentials fail the build.

Release APKs use a dedicated signing key. Uninstall an existing debug build before the first release install (this removes its app data). Later release APKs install as updates.
