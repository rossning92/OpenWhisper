"""Run the UI Automator documentation flow and collect its screenshots."""

import argparse
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.ross.speechtotext"


def run(command, **kwargs):
    return subprocess.run(command, check=True, timeout=300, **kwargs)


def save_png(path, data):
    if not data.startswith(bytes.fromhex("89504e470d0a1a0a")):
        raise RuntimeError(f"Invalid screenshot: {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    print(path)


def reconnect_accessibility(adb):
    # Instrumentation kills the app process. Rebind its already-enabled service.
    setting = ["shell", "settings", "get", "secure", "enabled_accessibility_services"]
    original = run(adb + setting, capture_output=True, text=True).stdout.strip()
    services = original.split(":")
    component = PACKAGE + "/" + PACKAGE + ".TypeAccessibilityService"
    if component not in services:
        return
    services.remove(component)
    put = adb + ["shell", "settings", "put", "secure", "enabled_accessibility_services"]
    try:
        run(put + [shlex.quote(":".join(services))], capture_output=True)
        time.sleep(0.3)
    finally:
        run(put + [shlex.quote(original)], capture_output=True)


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
    try:
        for apk in apks:
            run(adb + ["install", "-r", str(apk)])
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
        # Instrumentation ends the app process; restart its normal UI and overlay.
        run(adb + ["shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity"],
            capture_output=True)
        reconnect_accessibility(adb)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Screenshot capture failed: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr, file=sys.stderr)
        sys.exit(1)
