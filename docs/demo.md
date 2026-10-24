# Demo capture

The demo uses AndroidX UI Automator to play a bundled voice clip into the microphone, verify the transcription, and send it in the local chat.

## Requirements

- An unlocked, authorized phone
- The debug app and a downloaded model
- The [development requirements](development.md), Python 3, and `adb`
- FFmpeg when recording video

Use a quiet room. Media volume is restored after capture.

## Run from myscripts

From the `myscripts` directory:

```bash
CWD="$PWD/repos/SpeechToText" run_script r/android/gradle.sh assembleDebug assembleDebugAndroidTest
run_script repos/SpeechToText/scripts/capture_demo.py --serial DEVICE --video
```

`--video` saves `docs/screenshots/demo.mp4` with the bundled voice track. Recording starts in the chat and ends after the message is sent. Upload the MP4 through GitHub's README editor and put its attachment URL on its own line for inline playback.

Output is written to `docs/screenshots/`. The launcher reinstalls the debug APKs without clearing app data.
It temporarily selects English, locks portrait orientation, enables accessibility, and disables auto-finish. The original phone and app settings are restored afterward.

Only the two settings screenshots are saved. Use `--demo` to also check the message flow without video, or omit both flags for settings only. Use `--current NAME` for an extra screen without running tests, or `--output DIR` for another destination. Extend `app/src/androidTest/java/com/ross/speechtotext/DemoCaptureTest.java` for new flows.

Outside myscripts, build with `./gradlew assembleDebug assembleDebugAndroidTest`, then run `python3 scripts/capture_demo.py --serial DEVICE --video`.
