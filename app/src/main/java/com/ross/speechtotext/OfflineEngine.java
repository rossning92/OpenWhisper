package com.ross.speechtotext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** All native model access, including unload, stays on one worker. */
final class OfflineEngine {
    static { System.loadLibrary("offline-transcriber"); }
    private static final ScheduledThreadPoolExecutor WORKER = new ScheduledThreadPoolExecutor(1);
    private static long model;
    private static String modelPath;
    private static ScheduledFuture<?> expiry;
    static { WORKER.setRemoveOnCancelPolicy(true); }

    static native boolean supported();
    private static native long load(String path);
    private static native byte[] run(long model, float[] audio);
    private static native void unload(long model);

    private static void ensureLoaded(File file) {
        if (expiry != null) expiry.cancel(false);
        String path = file.getAbsolutePath();
        if (model != 0 && !path.equals(modelPath)) release();
        if (model == 0) {
            model = load(path);
            modelPath = path;
        }
    }

    private static void expireLater() {
        expiry = WORKER.schedule(OfflineEngine::release, 2, TimeUnit.MINUTES);
    }

    static Future<?> warmUp(File file) {
        return WORKER.submit(() -> {
            try { ensureLoaded(file); }
            finally { expireLater(); }
        });
    }

    static Future<String> transcribe(File file, float[] audio) {
        return WORKER.submit(() -> {
            try {
                ensureLoaded(file);
                return new String(run(model, audio), StandardCharsets.UTF_8).trim();
            } finally { expireLater(); }
        });
    }

    private static void release() {
        if (expiry != null) expiry.cancel(false);
        if (model != 0) unload(model);
        model = 0;
        modelPath = null;
    }

    static void releaseAsync() { WORKER.execute(OfflineEngine::release); }
}
