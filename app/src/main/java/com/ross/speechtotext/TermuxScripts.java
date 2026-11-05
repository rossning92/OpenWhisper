package com.ross.speechtotext;

import java.util.ArrayList;
import java.util.List;

final class TermuxScripts {
    private TermuxScripts() {}

    static List<String> parse(String value) {
        List<String> paths = new ArrayList<>();
        for (String line : value.split("\\r?\\n")) {
            String path = line.trim();
            if (!path.isEmpty()) paths.add(path);
        }
        return paths;
    }

    static String first(String value) {
        List<String> paths = parse(value);
        return paths.isEmpty() ? "" : paths.get(0);
    }
}
