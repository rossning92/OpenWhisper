package com.ross.speechtotext;

import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.MediaPlayer;
import android.content.res.AssetFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.content.ComponentName;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Real device documentation captures; run explicitly through capture_demo.py. */
@RunWith(AndroidJUnit4.class)
public class DemoCaptureTest {
    private static final long TIMEOUT = 10_000;
    private UiDevice device;
    private File output;

    private UiObject2 require(BySelector selector) {
        UiObject2 view = device.wait(Until.findObject(selector), TIMEOUT);
        assertNotNull("Missing " + selector + "; complete app setup first", view);
        return view;
    }

    private void capture(String name) {
        device.waitForIdle();
        assertTrue("Cannot capture " + name, device.takeScreenshot(new File(output, name + ".png")));
    }

    @Test
    public void captureDocumentation() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Opt-in documentation capture",
                "true".equals(arguments.getString("screenshots")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("Unlock the phone first", context.getSystemService(KeyguardManager.class).isKeyguardLocked());
        // Keep the app's accessibility service and its floating window active.
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        Configurator.getInstance().setWaitForIdleTimeout(1_000);
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        output = new File(context.getFilesDir(), "screenshots");
        assertTrue(output.isDirectory() || output.mkdirs());
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        require(By.text("Enable Open Whisper"));
        require(By.desc("Start dictation"));
        capture("01-ready");
        require(By.text("Offline model")).click();
        try {
            require(By.text("Done"));
            capture("02-models");
        } finally {
            require(By.text("Done")).click();
        }
        if ("true".equals(arguments.getString("demo"))) captureMessageDemo(context);
    }

    private void captureMessageDemo(Context context) throws Exception {
        reconnectAccessibility(context);
        assertFalse("Turn off Auto-finish after speech for the demo",
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean("auto_finish_speech", false));
        context.startActivity(new Intent(context, DemoChatActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        require(By.desc("Message Alex")).click();
        require(By.desc("Start dictation")).drag(new android.graphics.Point(
                device.getDisplayWidth() - 1, device.getDisplayHeight() / 3), 1_000);
        AudioManager audio = context.getSystemService(AudioManager.class);
        int volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        MediaPlayer player = new MediaPlayer();
        boolean recording = false;
        String videoPid = null;
        ParcelFileDescriptor videoOutput = null;
        long videoStarted = 0;
        try (AssetFileDescriptor clip = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().openFd("demo-message.wav")) {
            player.setDataSource(clip.getFileDescriptor(), clip.getStartOffset(), clip.getLength());
            for (AudioDeviceInfo output : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (output.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    assertTrue("Cannot route demo audio to speaker", player.setPreferredDevice(output));
                    break;
                }
            }
            player.prepare();
            CountDownLatch finished = new CountDownLatch(1);
            player.setOnCompletionListener(ignored -> finished.countDown());
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 3 / 4), 0);
            if ("true".equals(InstrumentationRegistry.getArguments().getString("video"))) {
                assertTrue("Another screen recording is running",
                        device.executeShellCommand("pidof screenrecord").trim().isEmpty());
                videoStarted = SystemClock.elapsedRealtime();
                videoOutput = InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                        .executeShellCommand("screenrecord --bit-rate 4000000 /data/local/tmp/speechtotext-demo.mp4");
                SystemClock.sleep(500);
                String pid = device.executeShellCommand("pidof screenrecord").trim();
                assertTrue("Screen recorder did not start: " + pid, pid.matches("[0-9]+"));
                videoPid = pid;
                SystemClock.sleep(1_000); // Establish the chat before the first action.
            }
            require(By.desc("Start dictation")).click();
            recording = true;
            require(By.desc("Cancel dictation"));
            if (videoPid != null) {
                try (FileWriter timing = new FileWriter(new File(output, "voice-offset.txt"))) {
                    timing.write(String.valueOf((SystemClock.elapsedRealtime() - videoStarted) / 1000.0));
                }
            }
            player.start();
            assertTrue("Demo audio did not finish", finished.await(20, TimeUnit.SECONDS));
            require(By.desc("Stop and transcribe")).click();
            recording = false;
            UiObject2 editor = require(By.desc("Message Alex"));
            assertTrue("Expected the recorded message to be inserted into the composer",
                    editor.wait(Until.textMatches("(?is).*alex.*on my way.*(ten|10) minutes.*"), 120_000));
            require(By.desc("Start dictation"));
            if (videoPid != null) SystemClock.sleep(3_500); // Let the viewer read the transcription.
            String message = require(By.desc("Message Alex")).getText();
            require(By.desc("Send message")).click();
            require(By.desc("Sent message").text(message));
            assertTrue("Composer should be empty after local send",
                    require(By.desc("Message Alex")).wait(Until.textMatches("(|Message Alex)"), TIMEOUT));
            if (videoPid != null) SystemClock.sleep(2_000); // Let the viewer read the result.
        } finally {
            player.release();
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
            if (recording) require(By.desc("Cancel dictation")).click();
            if (videoPid != null) {
                device.executeShellCommand("kill -2 " + videoPid);
                SystemClock.sleep(1_000); // Allow screenrecord to finalize the MP4.
            }
            if (videoOutput != null) videoOutput.close();
        }
    }

    private void reconnectAccessibility(Context context) {
        String services = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String component = new ComponentName(context, TypeAccessibilityService.class).flattenToString();
        java.util.List<String> enabled = new java.util.ArrayList<>(
                java.util.Arrays.asList(services == null ? new String[0] : services.split(":")));
        assertTrue("Enable Accessibility Service for message insertion", enabled.contains(component));
        if (TypeAccessibilityService.getInstance() != null) return;
        // Installing/instrumenting the app can leave its already-enabled service unbound.
        // Rebind only that service, then restore the exact original setting.
        UiAutomation automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS");
        try {
            enabled.remove(component);
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", enabled));
            SystemClock.sleep(300);
        } finally {
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services);
            automation.dropShellPermissionIdentity();
        }
        long deadline = SystemClock.uptimeMillis() + TIMEOUT;
        while (TypeAccessibilityService.getInstance() == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100);
        }
        assertNotNull("Accessibility service did not reconnect", TypeAccessibilityService.getInstance());
    }
}
