package com.ross.speechtotext;

import java.util.regex.Pattern;

final class SendMessageCommand {
    private static final Pattern SUFFIX = Pattern.compile(
            "(?iu)[\\s\\p{P}\\p{S}]*send(?:\\s+(?:a|the))?\\s+message"
                    + "[\\s\\p{P}\\p{S}]*$");

    static boolean isAtEnd(String text) {
        return SUFFIX.matcher(text).find();
    }

    static String removeFromEnd(String text) {
        return SUFFIX.matcher(text).replaceFirst("").trim();
    }

    private SendMessageCommand() {
    }
}
