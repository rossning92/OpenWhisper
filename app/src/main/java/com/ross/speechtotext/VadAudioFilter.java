package com.ross.speechtotext;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;

/** Keeps speech plus short boundary padding while dropping sustained silence. */
final class VadAudioFilter {
    private static final int PREFILL_FRAMES = 15;
    private static final int HANGOVER_FRAMES = 15;
    private static final int ONSET_FRAMES = 2;

    private final ArrayDeque<byte[]> prefill = new ArrayDeque<>();
    private int onsetFrames;
    private int hangoverFrames;
    private boolean inSpeech;

    void accept(byte[] frame, boolean speech, ByteArrayOutputStream output) {
        if (inSpeech) {
            if (speech) {
                hangoverFrames = HANGOVER_FRAMES;
                output.write(frame, 0, frame.length);
            } else if (hangoverFrames > 0) {
                hangoverFrames--;
                output.write(frame, 0, frame.length);
            } else {
                inSpeech = false;
                remember(frame);
            }
            return;
        }

        remember(frame);
        if (!speech) {
            onsetFrames = 0;
            return;
        }
        if (++onsetFrames < ONSET_FRAMES) return;

        for (byte[] buffered : prefill) output.write(buffered, 0, buffered.length);
        prefill.clear();
        onsetFrames = 0;
        hangoverFrames = HANGOVER_FRAMES;
        inSpeech = true;
    }

    private void remember(byte[] frame) {
        prefill.addLast(frame.clone());
        while (prefill.size() > PREFILL_FRAMES + 1) prefill.removeFirst();
    }
}
