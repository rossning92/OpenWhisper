# Development

## Requirements

- JDK 21
- Git
- Android SDK platform and build-tools 34
- Android NDK 28.2.13676358
- CMake 3.31.6

Set `ANDROID_HOME` to the Android SDK directory.

## Build and install

From the project directory, run:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat assembleDebug`. The first build downloads dependencies, including transcribe.cpp. Open the installed app and complete setup.

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
