#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../../scripts/r/android/android_helper.sh

APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.ross.speechtotext"

run_script ./build.sh

android_install_apk "$APK"

android_shell "appops set $PACKAGE SYSTEM_ALERT_WINDOW allow"
android_shell "pm grant $PACKAGE android.permission.RECORD_AUDIO"
android_shell "pm grant $PACKAGE android.permission.POST_NOTIFICATIONS"

# Enable the accessibility service without duplicating it.
ENABLED=$(android_shell "settings get secure enabled_accessibility_services" | tr -d '\r')
SVC="$PACKAGE/$PACKAGE.TypeAccessibilityService"
if [[ "$ENABLED" != *"$SVC"* ]]; then
    if [[ -z "$ENABLED" || "$ENABLED" == "null" ]]; then
        android_shell "settings put secure enabled_accessibility_services '$SVC'"
    else
        android_shell "settings put secure enabled_accessibility_services '$ENABLED:$SVC'"
    fi
fi
android_shell "settings put secure accessibility_enabled 1"

android_shell "am start -n $PACKAGE/.MainActivity"
