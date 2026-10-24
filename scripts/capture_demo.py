"""Run the UI Automator documentation flow and collect its screenshots."""

import argparse
from contextlib import contextmanager
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.ross.speechtotext"
ACCESSIBILITY_SERVICE = PACKAGE + "/" + PACKAGE + ".TypeAccessibilityService"
ACCESSIBILITY_ALIASES = {ACCESSIBILITY_SERVICE, PACKAGE + "/.TypeAccessibilityService"}
CAPTURE_SETTINGS = [
    ("system", "accelerometer_rotation", "0"),
    ("system", "user_rotation", "0"),
    ("secure", "accessibility_enabled", "1"),
]


def run(command, **kwargs):
    return subprocess.run(command, check=True, timeout=300, **kwargs)


def save_png(path, data):
    if not data.startswith(bytes.fromhex("89504e470d0a1a0a")):
        raise RuntimeError(f"Invalid screenshot: {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    print(path)


def get_setting(adb, namespace, name):
    return run(adb + ["shell", "settings", "get", namespace, name],
               capture_output=True, text=True).stdout.strip()


def set_setting(adb, namespace, name, value):
    action = "delete" if value in ("", "null") else "put"
    command = adb + ["shell", "settings", action, namespace, name]
    run(command + ([] if action == "delete" else [value]), capture_output=True)


@contextmanager
def configured_device(adb):
    original = {(namespace, name): get_setting(adb, namespace, name)
                for namespace, name, _ in CAPTURE_SETTINGS}
    original_services = get_setting(adb, "secure", "enabled_accessibility_services")
    locale_output = run(adb + ["shell", "cmd", "locale", "get-app-locales", PACKAGE],
                        capture_output=True, text=True).stdout.strip()
    original_locales = locale_output.rsplit("[", 1)[-1].removesuffix("]")
    services = [] if original_services in ("", "null") else original_services.split(":")
    if ACCESSIBILITY_SERVICE not in services:
        services.append(ACCESSIBILITY_SERVICE)
    try:
        run(adb + ["shell", "cmd", "locale", "set-app-locales", PACKAGE,
                   "--user", "0", "--locales", "en-US"])
        for namespace, name, value in CAPTURE_SETTINGS:
            set_setting(adb, namespace, name, value)
        set_setting(adb, "secure", "enabled_accessibility_services", ":".join(services))
        yield
    finally:
        run(adb + ["shell", "cmd", "locale", "set-app-locales", PACKAGE,
                   "--user", "0", "--locales", original_locales])
        for (namespace, name), value in original.items():
            set_setting(adb, namespace, name, value)
        # Toggling reconnects a service killed by instrumentation.
        if any(service in ACCESSIBILITY_ALIASES for service in original_services.split(":")):
            remaining = [service for service in services if service not in ACCESSIBILITY_ALIASES]
            set_setting(adb, "secure", "enabled_accessibility_services", ":".join(remaining))
            time.sleep(0.3)
        set_setting(adb, "secure", "enabled_accessibility_services", original_services)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--output", type=Path, default=ROOT / "docs/screenshots")
    parser.add_argument("--demo", action="store_true", help="Play bundled speech and demonstrate a local message")
    parser.add_argument("--video", action="store_true", help="Record the demo flow as MP4 with its bundled voice (requires ffmpeg)")
    parser.add_argument("--current", metavar="NAME", help="Capture current screen only; no test APK needed")
    args = parser.parse_args()
    if args.video:
        args.demo = True
        if not shutil.which("ffmpeg"):
            parser.error("--video requires ffmpeg")
    if args.current and (args.demo or not re.fullmatch(r"[A-Za-z0-9_-]+", args.current)):
        parser.error("--current requires a simple filename and cannot be combined with --demo")
    if not args.serial:
        lines = run(["adb", "devices"], capture_output=True, text=True).stdout.splitlines()
        devices = [line.split()[0] for line in lines if len(line.split()) == 2 and line.split()[1] == "device"]
        if len(devices) != 1:
            parser.error("Connect one authorized phone or specify --serial / ANDROID_SERIAL")
        args.serial = devices[0]
    adb = ["adb", "-s", args.serial]
    if args.current:
        data = run(adb + ["exec-out", "screencap", "-p"], capture_output=True).stdout
        save_png(args.output / f"{args.current}.png", data)
        return
    apks = [ROOT / "app/build/outputs/apk/debug/app-debug.apk",
            ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]
    if not all(apk.is_file() for apk in apks):
        parser.error("Build assembleDebug and assembleDebugAndroidTest first; see README")
    command = adb + ["shell", "am", "instrument", "-w", "-r",
                     "-e", "class", PACKAGE + ".DemoCaptureTest",
                     "-e", "screenshots", "true",
                     "-e", "demo", str(args.demo).lower(),
                     "-e", "video", str(args.video).lower(),
                     PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"]
    for apk in apks:
        run(adb + ["install", "-r", str(apk)])
    with configured_device(adb):
        try:
            result = run(command, capture_output=True, text=True)
            print(result.stdout)
            # am instrument can return zero even when the test fails.
            if "OK (1 test)" not in result.stdout:
                raise RuntimeError("UI Automator capture failed; see instrumentation output above")
            for name in ["01-ready", "02-models"]:
                data = run(adb + ["exec-out", "run-as", PACKAGE, "cat",
                                  f"files/screenshots/{name}.png"], capture_output=True).stdout
                save_png(args.output / f"{name}.png", data)
            if args.video:
                raw = args.output / "demo-silent.mp4"
                run(adb + ["pull", "/data/local/tmp/speechtotext-demo.mp4", str(raw)])
                offset = float(run(adb + ["exec-out", "run-as", PACKAGE, "cat",
                                          "files/screenshots/voice-offset.txt"], capture_output=True, text=True).stdout)
                video = args.output / "demo.mp4"
                run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(raw),
                     "-itsoffset", str(offset), "-i", str(ROOT / "app/src/androidTest/assets/demo-message.wav"),
                     "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac",
                     "-af", "apad", "-shortest", "-movflags", "+faststart", str(video)])
                raw.unlink()
                print(video)
        finally:
            run(adb + ["shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity"],
                capture_output=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Screenshot capture failed: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr, file=sys.stderr)
        sys.exit(1)
