package com.keyforge.nativeiiq.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTextTest {

    @Test
    void lineBreaksAndControlCharactersCannotStartAFakeLogLine() {
        assertEquals("2026_ERROR forged", LogText.safe("2026\nERROR forged"));
        assertEquals("a__c", LogText.safe("a\r\nc"));
        assertEquals("x_y", LogText.safe("x\u0000y"));
    }

    @Test
    void ordinaryValuesAreUnchangedAndNullStaysReadable() {
        assertEquals("2026-10-12T10:00:00Z", LogText.safe("2026-10-12T10:00:00Z"));
        assertEquals("null", LogText.safe(null));
    }

    @Test
    void longValuesAreCapped() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append('a');
        }
        String safe = LogText.safe(sb.toString());
        assertEquals(203, safe.length());
        assertTrue(safe.endsWith("..."));
    }
}
