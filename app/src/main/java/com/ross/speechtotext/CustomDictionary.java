package com.ross.speechtotext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dictionary correction for models without vocabulary prompts. See docs/dictionary.md. */
final class CustomDictionary {
    private static final double THRESHOLD = 0.18;
    // Explicit Unicode whitespace keeps tokenization consistent on Android and the JVM.
    private static final Pattern WORD = Pattern.compile(
            "[^\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A"
                    + "\\u2028\\u2029\\u202F\\u205F\\u3000]+");
    private final String vocabulary;
    private final boolean prompted;
    private final List<Entry> entries = new ArrayList<>();

    CustomDictionary(String vocabulary, boolean prompted) {
        this.vocabulary = vocabulary == null ? "" : vocabulary;
        this.prompted = prompted;
        Set<String> words = new LinkedHashSet<>();
        for (String line : this.vocabulary.split("\\R")) {
            String word = line.trim();
            if (!word.isEmpty()) words.add(word);
        }
        for (String word : words) {
            addEntry(word, key(word));
            if (word.contains("&")) {
                String expanded = key(word.replace("&", " and "));
                if (!expanded.equals(key(word))) addEntry(word, expanded);
            }
        }
    }

    String vocabulary() { return prompted ? vocabulary : ""; }

    private void addEntry(String word, String key) {
        if (supported(key)) entries.add(new Entry(word, key));
    }

    String correct(String text) {
        if (prompted || entries.isEmpty()) return text;
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) words.add(matcher.group());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < words.size();) {
            Entry best = null;
            int bestCount = 0;
            double bestScore = Double.MAX_VALUE;
            for (int count = Math.min(3, words.size() - i); count >= 1; count--) {
                StringBuilder candidate = new StringBuilder();
                boolean boundary = false;
                for (int j = 0; j < count; j++) {
                    String word = words.get(i + j);
                    if (j < count - 1 && suffixStart(word) < word.length()) boundary = true;
                    candidate.append(key(word));
                }
                String value = candidate.toString();
                if (boundary || !supported(value) || value.length() > 50) continue;
                String phonetic = letters(value) ? soundex(value) : null;
                for (Entry entry : entries) {
                    int max = Math.max(value.length(), entry.key.length());
                    if (Math.abs(value.length() - entry.key.length()) > Math.max(max * 0.25, 2)) continue;
                    double score = distance(value, entry.key) / (double) max;
                    if (phonetic != null && phonetic.equals(entry.phonetic)) score *= 0.3;
                    if (score < THRESHOLD && score < bestScore) {
                        best = entry;
                        bestCount = count;
                        bestScore = score;
                    }
                }
            }
            if (best == null) {
                result.add(words.get(i++));
            } else {
                String first = words.get(i);
                String last = words.get(i + bestCount - 1);
                result.add(first.substring(0, prefixEnd(first)) + preserveCase(first, best.word)
                        + last.substring(suffixStart(last)));
                i += bestCount;
            }
        }
        return String.join(" ", result);
    }

    private static String key(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().filter(CustomDictionary::alphanumeric).forEach(cp ->
                result.append(new String(Character.toChars(cp)).toLowerCase(Locale.ROOT)));
        return result.toString();
    }

    private static boolean alphanumeric(int cp) {
        int type = Character.getType(cp);
        return Character.isAlphabetic(cp) || Character.isDigit(cp)
                || type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
    }

    private static boolean supported(String key) {
        if (key.isEmpty()) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) return false;
        }
        return true;
    }

    private static boolean letters(String key) {
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) < 'a' || key.charAt(i) > 'z') return false;
        }
        return !key.isEmpty();
    }

    private static int prefixEnd(String word) {
        int offset = 0;
        while (offset < word.length()) {
            int cp = word.codePointAt(offset);
            if (alphanumeric(cp)) break;
            offset += Character.charCount(cp);
        }
        return offset;
    }

    private static int suffixStart(String word) {
        int offset = word.length();
        while (offset > 0) {
            int cp = word.codePointBefore(offset);
            if (alphanumeric(cp)) break;
            offset -= Character.charCount(cp);
        }
        return offset;
    }

    private static String preserveCase(String original, String replacement) {
        if (original.codePoints().allMatch(Character::isUpperCase)) {
            return replacement.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(original.codePointAt(0))) {
            int first = replacement.codePointAt(0);
            return new String(Character.toChars(Character.toUpperCase(first)))
                    + replacement.substring(Character.charCount(first));
        }
        return replacement;
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    // Keep first letter, remove h/w, dedupe, remove vowels,
    // then truncate/pad to four characters. Do not encode the first letter.
    private static String soundex(String word) {
        StringBuilder result = new StringBuilder().append(word.charAt(0));
        char previous = word.charAt(0);
        for (int i = 1; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == 'h' || c == 'w') continue;
            char digit = soundexDigit(c);
            if (digit != previous && digit != '0') result.append(digit);
            previous = digit;
        }
        while (result.length() < 4) result.append('0');
        return result.substring(0, 4);
    }

    private static char soundexDigit(char c) {
        if ("bfpv".indexOf(c) >= 0) return '1';
        if ("cgjkqsxz".indexOf(c) >= 0) return '2';
        if ("dt".indexOf(c) >= 0) return '3';
        if (c == 'l') return '4';
        if (c == 'm' || c == 'n') return '5';
        if (c == 'r') return '6';
        return '0';
    }

    private static final class Entry {
        final String word;
        final String key;
        final String phonetic;
        Entry(String word, String key) {
            this.word = word;
            this.key = key;
            this.phonetic = letters(key) ? soundex(key) : null;
        }
    }
}
