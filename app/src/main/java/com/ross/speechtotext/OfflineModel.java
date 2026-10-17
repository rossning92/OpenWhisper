package com.ross.speechtotext;

import android.content.Context;
import java.io.File;

final class OfflineModel {
    static final long BYTES = 731357568L;
    static final String SHA256 = "4b50b6dd862bf6e346929aaf4f5eaacec003bfa3f56462d6c874b41ef2f38795";
    static final String NAME = "parakeet-unified-en-0.6b-Q8_0.gguf";
    static final String URL = "https://huggingface.co/handy-computer/parakeet-unified-en-0.6b-gguf/resolve/main/" + NAME;
    static File file(Context context) { return new File(context.getNoBackupFilesDir(), NAME); }
    // Only a complete, checksum-verified download is renamed to this path.
    static boolean ready(File file) { return file.isFile() && file.length() == BYTES; }
}
