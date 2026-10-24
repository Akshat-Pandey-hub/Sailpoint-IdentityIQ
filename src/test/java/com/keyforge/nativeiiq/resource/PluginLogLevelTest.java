package com.keyforge.nativeiiq.resource;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginLogLevelTest {

    @Test
    void normalIsTheDefault() {
        assertEquals(Level.INFO, PluginLogLevel.levelFor(null));
        assertEquals(Level.INFO, PluginLogLevel.levelFor(""));
        assertEquals(Level.INFO, PluginLogLevel.levelFor("normal"));
        assertEquals(Level.INFO, PluginLogLevel.levelFor("verbose")); // unknown values fall back to normal
    }

    @Test
    void debugAndOffOnlyWhenSet() {
        assertEquals(Level.DEBUG, PluginLogLevel.levelFor("debug"));
        assertEquals(Level.DEBUG, PluginLogLevel.levelFor(" DEBUG "));
        assertEquals(Level.OFF, PluginLogLevel.levelFor("off"));
    }
}
