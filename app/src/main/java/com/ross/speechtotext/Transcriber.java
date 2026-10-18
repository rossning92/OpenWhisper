package com.ross.speechtotext;

/**
 * One local recording and transcription session.
 */
interface Transcriber extends AutoCloseable {
    interface Listener {
        void onAmplitude(float amplitude);
        void onSpeechEnded();
        void onFailure(String message);
    }

    void start() throws Exception;

    void stop();

    String finish() throws Exception;

    default String getFinishingMessage() {
        return null;
    }

    default boolean canRetry() {
        return false;
    }

    @Override
    void close();
}
