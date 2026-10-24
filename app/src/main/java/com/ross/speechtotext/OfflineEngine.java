package com.ross.speechtotext;

import android.util.Log;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** All native model access, including unload, stays on one worker. */
final class OfflineEngine {
    private static final String TAG = "OfflineEngine";
    static { System.loadLibrary("offline-transcriber"); }
    private static final ScheduledThreadPoolExecutor WORKER = new ScheduledThreadPoolExecutor(1);
    private static long model;
    private static String modelPath;
    private static ScheduledFuture<?> expiry;
    static { WORKER.setRemoveOnCancelPolicy(true); }

    static native boolean supported();
    private static native long load(String path);
    private static native byte[] run(long model, float[] audio, byte[] vocabulary);
    private static native long streamBegin(long model);
    private static native void streamFeed(long stream, float[] audio);
    private static native byte[] streamFinish(long stream);
    private static native void streamDestroy(long stream);
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

    static Future<String> transcribe(File file, float[] audio, String vocabulary) {
        return WORKER.submit(() -> {
            try {
                ensureLoaded(file);
                return new String(run(model, audio, vocabulary.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8).trim();
            } finally { expireLater(); }
        });
    }

    static Stream startStream(File file) {
        return new Stream(file);
    }

    static final class Stream implements AutoCloseable {
        private final Future<Long> handle;
        private volatile Exception failure;
        private boolean finished;
        private boolean closed;

        Stream(File file) {
            handle = WORKER.submit(() -> {
                ensureLoaded(file);
                long value = streamBegin(model);
                Log.i(TAG, "Streaming started: model=" + file.getName());
                return value;
            });
        }

        synchronized void feed(float[] audio) {
            if (closed || finished || audio.length == 0) return;
            WORKER.submit(() -> {
                try { streamFeed(handle.get(), audio); }
                catch (Exception error) {
                    failure = error;
                    throw new RuntimeException(error);
                }
            });
        }

        synchronized Future<String> finish() {
            if (closed || finished) throw new IllegalStateException("Stream is already finished");
            finished = true;
            return WORKER.submit(() -> {
                long value = 0;
                try {
                    value = handle.get();
                    if (failure != null) throw new Exception("Streaming feed failed", failure);
                    return new String(streamFinish(value), StandardCharsets.UTF_8).trim();
                } finally {
                    if (value != 0) streamDestroy(value);
                    synchronized (Stream.this) { closed = true; }
                    expireLater();
                }
            });
        }

        @Override public synchronized void close() {
            if (closed || finished) return;
            closed = true;
            WORKER.submit(() -> {
                try { streamDestroy(handle.get()); }
                catch (Exception ignored) { }
                finally { expireLater(); }
            });
        }
    }

    private static void release() {
        if (expiry != null) expiry.cancel(false);
        if (model != 0) unload(model);
        model = 0;
        modelPath = null;
    }

    static void releaseAsync() { WORKER.execute(OfflineEngine::release); }
}
