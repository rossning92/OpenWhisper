# Releases

## GitHub release

Push a tag such as `v1.0.1` to build a signed ARM64 APK and publish it with SHA-256 checksums on GitHub Releases.

Tags must use `vMAJOR.MINOR.PATCH`, with each component from 0 through 999. The version code is `MAJOR * 1000000 + MINOR * 1000 + PATCH` and must increase for updates.

GitHub Actions requires these repository secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Keep a private backup of the keystore and passwords. Future updates must use the same signing key.

## Local release

Set `RELEASE_STORE_FILE` (absolute keystore path), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_VERSION_CODE`, and `RELEASE_VERSION_NAME`.

From the project directory, run `./gradlew assembleRelease`, or `gradlew.bat assembleRelease` on Windows. Missing signing credentials fail the build. The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Release APKs use a dedicated signing key. Uninstall an existing debug build before the first release install; this removes its app data. Later release APKs install as updates.
