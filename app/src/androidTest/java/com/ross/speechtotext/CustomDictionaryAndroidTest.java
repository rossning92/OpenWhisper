package com.ross.speechtotext;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class CustomDictionaryAndroidTest {
    @Test public void initializesAndSplitsUnicodeWhitespaceOnAndroid() {
        CustomDictionary dictionary = new CustomDictionary("OpenAI", false);
        assertEquals("OpenAI works", dictionary.correct("Open\u00a0AI\u3000works"));
        assertEquals("OpenAI works", dictionary.correct("Open\tAI\nworks"));
    }
}
