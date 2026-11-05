package com.ross.speechtotext;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TermuxScriptsAndroidTest {
    private static class RecordingContext extends ContextWrapper {
        Intent serviceIntent;
        Intent activityIntent;
        boolean granted = true;
        RecordingContext() {
            super(InstrumentationRegistry.getInstrumentation().getTargetContext());
        }
        @Override public int checkSelfPermission(String permission) {
            return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        }
        @Override public ComponentName startService(Intent intent) {
            serviceIntent = intent;
            return intent.getComponent();
        }
        @Override public void startActivity(Intent intent) { activityIntent = intent; }
    }

    @Test public void selectedScriptsProduceDifferentCommandsAndKeepTextIntact() {
        RecordingContext context = new RecordingContext();
        String text = "quote ' and $variable\nnext line";
        FloatingService.runTermuxScript(context, text, "~/first.sh");
        assertArrayEquals(new String[]{"--", "/data/data/com.termux/files/home/first.sh", text},
                context.serviceIntent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS"));
        FloatingService.runTermuxScript(context, text, "/path with spaces/second.sh");
        assertArrayEquals(new String[]{"--", "/path with spaces/second.sh", text},
                context.serviceIntent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS"));
    }

    @Test public void permissionHandoffKeepsSelectedScript() {
        RecordingContext context = new RecordingContext();
        context.granted = false;
        FloatingService.runTermuxScript(context, "hello", "~/second.sh");
        assertNull(context.serviceIntent);
        assertEquals("/data/data/com.termux/files/home/second.sh",
                context.activityIntent.getStringExtra(TermuxPermissionActivity.EXTRA_SCRIPT_PATH));
        assertEquals("hello", context.activityIntent.getStringExtra(TermuxPermissionActivity.EXTRA_TRANSCRIPTION));
    }

    @Test public void savedListUsesFirstScriptForDefaultDelivery() {
        RecordingContext context = new RecordingContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean existed = preferences.contains("termux_script_path");
        String original = preferences.getString("termux_script_path", "");
        try {
            assertTrue(preferences.edit().putString("termux_script_path", "~/first.sh\n~/second.sh").commit());
            FloatingService.runTermuxScript(context, "hello");
            assertEquals("/data/data/com.termux/files/home/first.sh",
                    context.serviceIntent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")[1]);
        } finally {
            SharedPreferences.Editor edit = preferences.edit();
            if (existed) edit.putString("termux_script_path", original);
            else edit.remove("termux_script_path");
            assertTrue(edit.commit());
        }
    }
}
