package com.ross.speechtotext;

import android.content.Context;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** One recording's VAD state, owned entirely by the capture thread. */
final class SileroVad implements AutoCloseable {
    static final int FRAME_SAMPLES = 512;
    static final int FRAME_MS = 32;
    private static final String MODEL = "silero-vad-v6.2-F32.gguf";
    static { System.loadLibrary("offline-transcriber"); }
    private long handle;

    SileroVad(Context context) throws Exception {
        File model = new File(context.getFilesDir(), MODEL);
        if (!model.isFile()) {
            File temporary = new File(context.getFilesDir(), MODEL + ".tmp");
            try (InputStream input = context.getAssets().open(MODEL)) {
                Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary.toPath(), model.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                temporary.delete();
            }
        }
        handle = create(model.getAbsolutePath());
    }

    float probability(float[] frame) {
        if (handle == 0) throw new IllegalStateException("VAD is closed");
        return process(handle, frame);
    }

    @Override public void close() {
        if (handle != 0) destroy(handle);
        handle = 0;
    }

    private static native long create(String path);
    private static native float process(long handle, float[] frame);
    private static native void destroy(long handle);
}
