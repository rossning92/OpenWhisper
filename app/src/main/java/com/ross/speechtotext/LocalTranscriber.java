package com.ross.speechtotext;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.ExecutionException;

final class LocalTranscriber implements Transcriber {
    private static final String TAG = "LocalTranscriber";
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_BYTES = SAMPLE_RATE * 2 * 5 * 60;
    private final OfflineModel modelInfo;
    private final File model;
    private final Listener listener;
    private final String vocabulary;
    private final boolean autoFinish;
    private final Context context;
    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
    private AudioRecord recorder;
    private Thread reader;
    private volatile boolean recording;
    private volatile String failure;
    private volatile boolean closed;

    LocalTranscriber(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.modelInfo = OfflineModel.selected(context);
        this.model = modelInfo.file(context);
        this.listener = listener;
        this.autoFinish = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("auto_finish_speech", false);
        this.vocabulary = modelInfo == OfflineModel.QWEN
                ? PreferenceManager.getDefaultSharedPreferences(context)
                        .getString("custom_dictionary", "") : "";
    }

    @Override public void start() throws Exception {
        if (!modelInfo.ready(model)) throw new Exception(context.getString(R.string.model_required, modelInfo.label));
        if (!OfflineEngine.supported()) throw new Exception(context.getString(R.string.offline_unsupported));
        int size = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (size <= 0) throw new Exception(context.getString(R.string.recording_unavailable));
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(size, 4096));
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            recorder = null;
            throw new Exception(context.getString(R.string.microphone_open_failed));
        }
        recorder.startRecording();
        if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            throw new Exception(context.getString(R.string.microphone_start_failed));
        }
        recording = true;
        AudioRecord current = recorder;
        reader = new Thread(() -> capture(current), "OfflineAudio");
        reader.start();
        OfflineEngine.warmUp(model);
    }

    private void capture(AudioRecord current) {
        byte[] buffer = new byte[SileroVad.FRAME_SAMPLES * 2];
        // Keep VAD creation, use and release on the capture thread.
        try (SileroVad vad = autoFinish ? new SileroVad(context) : null) {
            SpeechEndpoint endpoint = new SpeechEndpoint();
            float[] pcm = vad == null ? null : new float[SileroVad.FRAME_SAMPLES];
            boolean speech = false;
            int filled = 0;
            while (recording) {
                int count = current.read(buffer, filled, buffer.length - filled);
                if (count < 0) {
                    if (recording) throw new Exception(context.getString(R.string.microphone_read_failed, count));
                    break;
                }
                if (count == 0) continue;
                synchronized (audio) {
                    if (audio.size() + count > MAX_BYTES) {
                        throw new Exception(context.getString(R.string.recording_too_long));
                    }
                    audio.write(buffer, filled, count);
                }
                filled += count;
                if (filled < buffer.length) continue;
                filled = 0;
                double squares = 0;
                for (int i = 0; i < buffer.length; i += 2) {
                    short sample = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
                    float value = sample / 32768.0f;
                    squares += value * value;
                    if (pcm != null) pcm[i / 2] = value;
                }
                listener.onAmplitude(AudioLevel.fromNormalizedAmplitude(
                        Math.sqrt(squares / SileroVad.FRAME_SAMPLES)));
                if (vad == null) continue;
                // Hysteresis prevents uncertain frames from cutting off quiet speech.
                speech = vad.probability(pcm) >= (speech ? 0.35f : 0.5f);
                if (endpoint.accept(speech)) {
                    if (recording) listener.onSpeechEnded();
                    return;
                }
            }
        } catch (Exception error) {
            if (error instanceof IllegalStateException) {
                Log.e(TAG, "Native VAD failed", error);
                failure = context.getString(R.string.offline_transcription_failed);
            } else {
                failure = error.getMessage();
            }
            listener.onFailure(failure);
        }
    }

    @Override public void stop() {
        recording = false;
        if (recorder != null) {
            try { recorder.stop(); } catch (IllegalStateException ignored) { }
        }
        if (reader != null && reader != Thread.currentThread()) {
            try { reader.join(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }
        reader = null;
        if (recorder != null) recorder.release();
        recorder = null;
    }

    @Override public String finish() throws Exception {
        if (closed) throw new Exception(context.getString(R.string.recording_cancelled));
        if (failure != null) throw new Exception(failure);
        byte[] bytes;
        synchronized (audio) { bytes = audio.toByteArray(); }
        if (bytes.length < 2) throw new Exception(context.getString(R.string.no_audio_recorded));
        float[] pcm = new float[bytes.length / 2];
        for (int i = 0; i < pcm.length; ++i) {
            pcm[i] = (short) ((bytes[i * 2] & 255) | (bytes[i * 2 + 1] << 8)) / 32768.0f;
        }
        try {
            String text = OfflineEngine.transcribe(model, pcm, vocabulary).get();
            if (closed) throw new Exception(context.getString(R.string.recording_cancelled));
            return text;
        } catch (ExecutionException error) {
            Log.e(TAG, "Native transcription failed", error.getCause());
            throw new Exception(context.getString(R.string.offline_transcription_failed), error.getCause());
        }
    }

    @Override public String getFinishingMessage() { return context.getString(R.string.transcribing_on_device); }
    @Override public boolean canRetry() {
        synchronized (audio) { return !closed && failure == null && audio.size() > 0; }
    }
    @Override public void close() {
        closed = true;
        stop();
        synchronized (audio) { audio.reset(); }
    }
}
