package com.ross.speechtotext;

/** Ends speech after 1.5 seconds of silence, using 32 ms Silero frames. */
final class SpeechEndpoint {
    private static final int MIN_SPEECH_MS = 200;
    private static final int SILENCE_MS = 1500;
    private int speechFrames;
    private int silentFrames;
    private boolean heardSpeech;

    boolean accept(boolean speech) {
        if (speech) {
            silentFrames = 0;
            if (++speechFrames * SileroVad.FRAME_MS >= MIN_SPEECH_MS) heardSpeech = true;
        } else {
            speechFrames = 0;
            if (heardSpeech && ++silentFrames * SileroVad.FRAME_MS >= SILENCE_MS) {
                return true;
            }
        }
        return false;
    }
}
