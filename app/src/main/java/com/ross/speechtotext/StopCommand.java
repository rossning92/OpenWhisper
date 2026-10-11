package com.ross.speechtotext;

import java.util.regex.Pattern;

final class StopCommand {
    private static final Pattern SUFFIX = Pattern.compile(
            "(?iu)[\\s\\p{P}\\p{S}]*stop[\\s\\p{P}\\p{S}]*$");

    static boolean isAtEnd(String text) {
        return SUFFIX.matcher(text).find();
    }

    private StopCommand() {
    }
}
