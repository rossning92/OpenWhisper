# Open Whisper

https://github.com/user-attachments/assets/49acff04-5619-49ba-93c3-5d43468ca49a

On-device dictation demonstrated in a local chat.

<p>
  <img src="docs/screenshots/01-ready.png" width="240" alt="Settings and floating dictation button">
  <img src="docs/screenshots/02-models.png" width="240" alt="Offline model picker">
</p>

Android floating dictation app. Transcribes on device using Parakeet or Qwen3-ASR via transcribe.cpp; audio stays on the device. Recordings are limited to 5 minutes.

Parakeet transcription streams while recording, so long dictations need little final processing. Qwen3-ASR uses batch transcription.

- Requirements: Android 8.0+ and an ARM64 CPU with FP16 SIMD and dot-product support.
- Setup: open the app, grant microphone and overlay permissions, choose Parakeet (English, 731 MB) or Qwen3-ASR (multilingual, 850 MB), then download it. Internet is needed for the download; transcription works offline afterward.
- Custom dictionary: in Settings, enter one word or phrase per line, most important first. Qwen3-ASR uses these as hints; Parakeet ignores them. Blank lines and duplicates are ignored. Clear the list to disable it. Long lists may be shortened to fit the model's context.
- Use: focus a text field, tap the floating button to record, then tap Send to transcribe. Enable Accessibility Service from the app's main screen to insert text. Without a focused field or accessibility, choose the fallback destination in Settings. Tap X to cancel; drag the control to move it.
- Voice detection: on-device Silero VAD 6.2 removes sustained silence before transcription while retaining short pauses and about 480 ms around speech boundaries. Enable "Auto-finish after speech" to also send after 1.5 seconds of silence following speech. Auto-finish is off by default; Send and X still work manually.
- When no input field is focused: choose "Copy to clipboard" (default) or "Run Termux command" in Settings. Also applies when accessibility is unavailable. Changes take effect immediately.
- Optional Termux action: set "Termux script path" in Settings to an absolute path or `~/` path, e.g. `~/bin/handle_transcription.sh`. Requires Termux, your script, and permission to run Termux commands. The script runs with Bash from the Termux home directory and receives the transcription as its first argument. No path is configured by default; an unset or incorrectly formatted path shows an error. Existing users must set their script path after updating.

## Development

- [Build and install the app](docs/development.md)
- [Capture the demo and screenshots](docs/demo.md)
- [Create a release](docs/releases.md)
