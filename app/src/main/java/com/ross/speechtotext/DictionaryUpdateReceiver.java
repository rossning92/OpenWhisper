package com.ross.speechtotext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BadParcelableException;

import androidx.preference.PreferenceManager;

import java.util.LinkedHashSet;
import java.util.Set;

/** Receives dictionary updates from other apps. */
public class DictionaryUpdateReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.ross.speechtotext.UPDATE_DICTIONARY";
    private static final int MAX_LENGTH = 32000;

    @Override
    public void onReceive(Context context, Intent intent) {
        reply(Activity.RESULT_CANCELED, "Invalid dictionary update");
        if (!ACTION.equals(intent.getAction())) return;
        String text;
        String mode;
        try {
            text = intent.getStringExtra("text");
            mode = intent.getStringExtra("mode");
        } catch (BadParcelableException | ClassCastException invalidExtras) {
            return;
        }
        if (mode == null) mode = "append";
        if (text == null
                || text.length() > MAX_LENGTH
                || !("append".equals(mode) || "replace".equals(mode))) {
            return;
        }
        boolean replace = "replace".equals(mode);
        String requested = normalize(text);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        String updated = replace ? requested : normalize(
                prefs.getString("custom_dictionary", "") + "\n" + requested);
        if (updated.length() > MAX_LENGTH) {
            reply(Activity.RESULT_CANCELED, "Dictionary exceeds 32,000 characters");
        } else if (prefs.edit().putString("custom_dictionary", updated).commit()) {
            reply(Activity.RESULT_OK, "Dictionary updated");
        } else {
            reply(Activity.RESULT_CANCELED, "Could not save dictionary");
        }
    }

    private void reply(int code, String message) {
        if (isOrderedBroadcast()) setResult(code, message, null);
    }

    private static String normalize(String text) {
        Set<String> entries = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            String entry = line.trim();
            if (!entry.isEmpty()) entries.add(entry);
        }
        return String.join("\n", entries);
    }
}
