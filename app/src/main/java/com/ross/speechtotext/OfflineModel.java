package com.ross.speechtotext;

import android.content.Context;
import androidx.preference.PreferenceManager;
import java.io.File;

final class OfflineModel {
    static final String PREFERENCE = "offline_model_choice";
    static final OfflineModel PARAKEET = new OfflineModel(
            "parakeet", "Parakeet 0.6B Q8", "English", 731357568L,
            "4b50b6dd862bf6e346929aaf4f5eaacec003bfa3f56462d6c874b41ef2f38795",
            "parakeet-unified-en-0.6b-Q8_0.gguf",
            "https://huggingface.co/handy-computer/parakeet-unified-en-0.6b-gguf/resolve/main/");
    static final OfflineModel QWEN = new OfflineModel(
            "qwen", "Qwen3-ASR 0.6B Q8", "multilingual", 850423456L,
            "f081b2d5e23bd669d92cc331d722a8a0681943b8e6f34b48996fd5c319b5acd8",
            "Qwen3-ASR-0.6B-Q8_0.gguf",
            "https://huggingface.co/handy-computer/Qwen3-ASR-0.6B-gguf/resolve/main/");
    static final OfflineModel[] ALL = {PARAKEET, QWEN};

    final String key;
    final String label;
    final String languages;
    final long bytes;
    final String sha256;
    final String name;
    final String url;

    private OfflineModel(String key, String label, String languages, long bytes,
                         String sha256, String name, String baseUrl) {
        this.key = key;
        this.label = label;
        this.languages = languages;
        this.bytes = bytes;
        this.sha256 = sha256;
        this.name = name;
        this.url = baseUrl + name;
    }

    static OfflineModel selected(Context context) {
        String key = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREFERENCE, PARAKEET.key);
        for (OfflineModel model : ALL) {
            if (model.key.equals(key)) return model;
        }
        return PARAKEET;
    }

    void select(Context context) {
        if (selected(context) == this) return;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREFERENCE, key).apply();
        OfflineEngine.releaseAsync();
    }

    File file(Context context) { return new File(context.getNoBackupFilesDir(), name); }
    boolean ready(Context context) { return ready(file(context)); }
    boolean ready(File file) { return file.isFile() && file.length() == bytes; }
    int megabytes() { return (int) Math.round(bytes / 1_000_000.0); }
}
