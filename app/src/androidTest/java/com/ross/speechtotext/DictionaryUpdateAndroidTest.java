package com.ross.speechtotext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DictionaryUpdateAndroidTest {
    private Context context;
    private SharedPreferences prefs;
    private String original;

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        original = prefs.getString("custom_dictionary", null);
        prefs.edit().putString("custom_dictionary", "Existing").commit();
    }

    @After public void restore() {
        prefs.edit().putString("custom_dictionary", original).commit();
    }

    private int request(String mode, String text) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(Activity.RESULT_CANCELED);
        context.sendOrderedBroadcast(new Intent(context, DictionaryUpdateReceiver.class)
                .setAction(DictionaryUpdateReceiver.ACTION)
                .putExtra("mode", mode).putExtra("text", text), null,
                new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        result.set(getResultCode());
                        done.countDown();
                    }
                }, null, Activity.RESULT_CANCELED, null, null);
        assertTrue("Update must complete", done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    @Test public void appendImmediatelyRemovesDuplicates() throws InterruptedException {
        assertEquals(Activity.RESULT_OK, request("append", " OpenAI \nExisting\nOpenAI\n"));
        assertEquals("Existing\nOpenAI", prefs.getString("custom_dictionary", ""));
    }

    @Test public void invalidModeDoesNotWrite() throws InterruptedException {
        assertEquals(Activity.RESULT_CANCELED, request("invalid", "Replacement"));
        assertEquals("Existing", prefs.getString("custom_dictionary", ""));
    }

    @Test public void emptyReplacementClearsDictionaryImmediately() throws InterruptedException {
        assertEquals(Activity.RESULT_OK, request("replace", ""));
        assertEquals("", prefs.getString("custom_dictionary", ""));
    }

    @Test public void replacementOverwritesDictionary() throws InterruptedException {
        assertEquals(Activity.RESULT_OK, request("replace", "Qwen\nOpenAI"));
        assertEquals("Qwen\nOpenAI", prefs.getString("custom_dictionary", ""));
    }

    @Test public void defaultModeAppends() throws InterruptedException {
        assertEquals(Activity.RESULT_OK, request(null, "Qwen"));
        assertEquals("Existing\nQwen", prefs.getString("custom_dictionary", ""));
    }

    @Test public void oversizedInputDoesNotWrite() throws InterruptedException {
        assertEquals(Activity.RESULT_CANCELED, request("replace", new String(new char[32001])));
        assertEquals("Existing", prefs.getString("custom_dictionary", ""));
    }

    @Test public void oversizedAppendDoesNotWrite() throws InterruptedException {
        String text = new String(new char[32000]).replace('\0', 'a');
        assertEquals(Activity.RESULT_CANCELED, request("append", text));
        assertEquals("Existing", prefs.getString("custom_dictionary", ""));
    }

    @Test public void missingTextDoesNotWrite() throws InterruptedException {
        assertEquals(Activity.RESULT_CANCELED, request("replace", null));
        assertEquals("Existing", prefs.getString("custom_dictionary", ""));
    }

    @Test public void shellBroadcastUpdatesDictionaryAndReportsSuccess() throws Exception {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(
                        "am broadcast -a com.ross.speechtotext.UPDATE_DICTIONARY "
                        + "-n com.ross.speechtotext/.DictionaryUpdateReceiver --es text OpenAI");
        try (Scanner scanner = new Scanner(new ParcelFileDescriptor.AutoCloseInputStream(output))) {
            String result = scanner.useDelimiter("\\A").next();
            assertTrue(result, result.contains("result=-1"));
            assertTrue(result, result.contains("Dictionary updated"));
        }
        assertEquals("Existing\nOpenAI", prefs.getString("custom_dictionary", ""));
    }
}
