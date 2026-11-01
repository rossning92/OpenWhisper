package com.ross.speechtotext;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CustomDictionaryTest {
    @Test public void splitsUnicodeWhitespace() {
        assertEquals("OpenAI works", correct("OpenAI", "Open\u00a0AI\u3000works"));
        assertEquals("OpenAI works", correct("OpenAI", "Open\tAI\nworks"));
    }

    private static String correct(String words, String text) {
        return new CustomDictionary(words, false).correct(text);
    }

    @Test public void emptyDictionaryDoesNotChangeWhitespace() {
        assertEquals("  hello\nworld  ", correct("\n \n", "  hello\nworld  "));
    }

    @Test public void vocabularyModelsReceiveHintsWithoutFuzzyCorrection() {
        String words = "Candy\n\u4f60\u597d";
        CustomDictionary dictionary = new CustomDictionary(words, true);
        assertEquals(words, dictionary.vocabulary());
        assertEquals("candee  \u4f60\u597d", dictionary.correct("candee  \u4f60\u597d"));
        assertEquals("", new CustomDictionary(words, false).vocabulary());
    }

    @Test public void phoneticMatchesBeatDefaultEditDistanceThreshold() {
        assertEquals("Candy works", correct("Candy", "candee works"));
        assertEquals("Robert", correct("Robert", "Rupert"));
        assertEquals("unrelated text", correct("Candy", "unrelated text"));
    }

    @Test public void editDistanceUsesStrictDefaultThreshold() {
        assertEquals("abcdef", correct("abcdef", "abcdeg"));
        assertEquals("12345", correct("12346", "12345"));
        // 9/50 = 0.18 is rejected, even though the length guard allows it.
        String candidate = "11111111111111111111111111111111111111111111111111";
        String replacement = "22222222211111111111111111111111111111111111111111";
        assertEquals(candidate, correct(replacement, candidate));
    }

    @Test public void closestNgramPreservesFollowingWordsAndPunctuationBoundaries() {
        assertEquals("ChargeBee, che permette", correct("ChargeBee", "Charge B, che permette"));
        assertEquals("OpenAI GPT model", correct("OpenAI\nGPT", "Open AI GPT model"));
        assertEquals("R&D", correct("R&D", "R and D"));
        assertEquals("R, and D", correct("R&D", "R, and D"));
        assertEquals("using MacBook Pro", correct("MacBook Pro", "using Mac Book Pro"));
    }

    @Test public void dictionaryOrderBreaksEqualScoresAndDuplicatesAreIgnored() {
        assertEquals("Candy", correct("\n Candy \nCANDY\nCandy\n", "candy"));
        assertEquals("CANDY", correct("CANDY\nCandy", "candy"));
    }

    @Test public void punctuationAndCasePreserveTokenFormatting() {
        assertEquals("\u300cCandy\u3002\u300d", correct("Candy", "\u300ccandee\u3002\u300d"));
        assertEquals("CHARGEBEE works", correct("ChargeBee", "CHARGE B works"));
        assertEquals("Candy", correct("candy", "Candee"));
        assertEquals("GPT-4", correct("GPT-4", "GPT4"));
    }

    @Test public void nonAsciiAndNumbersDoNotGetPhoneticBoosts() {
        assertEquals("\u4f60\u597d\u3002", correct("\u4f60\u53f7", "\u4f60\u597d\u3002"));
        assertEquals("cand\u00e9e", correct("Candy", "cand\u00e9e"));
        assertEquals("candy\u00b2", correct("Candy", "candy\u00b2"));
        assertEquals("gpt5", correct("GPT4", "gpt5"));
    }

    @Test public void candidatesLongerThanFiftyCharactersAreSkipped() {
        String longWord = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
        assertEquals(longWord, correct(longWord.toUpperCase(), longWord));
    }
}
