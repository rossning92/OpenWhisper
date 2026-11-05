package com.ross.speechtotext;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class TermuxScriptsTest {
    @Test public void preservesOrderAndSpacesWhileIgnoringBlankLines() {
        assertEquals(Arrays.asList("~/first.sh", "/path with spaces/second.sh", "~/third.sh"),
                TermuxScripts.parse("  ~/first.sh \r\n\n /path with spaces/second.sh\n~/third.sh"));
    }

    @Test public void keepsLegacyPathAndFirstScriptDefault() {
        assertEquals("~/legacy.sh", TermuxScripts.first("~/legacy.sh"));
        assertEquals("~/first.sh", TermuxScripts.first("\n ~/first.sh\n~/second.sh"));
        assertEquals("", TermuxScripts.first(" \n\r\n"));
    }
}
