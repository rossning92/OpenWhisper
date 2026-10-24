package com.ross.speechtotext;

import android.app.Application;
import androidx.preference.PreferenceManager;

public final class SpeechToTextApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Remove credentials and obsolete settings from earlier cloud-enabled builds.
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .remove("openai_api_key")
                .remove("transcription_mode")
                .remove("transcription_prompt")
                .remove("audio_bitrate")
                .apply();
    }
}
