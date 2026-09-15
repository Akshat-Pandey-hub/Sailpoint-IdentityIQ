package com.keyforge.iiq.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    @Test
    void constructsWithValidValues() {
        AppConfig config = new AppConfig("https://host/identityiq", "spadmin", "secret");
        assertEquals("https://host/identityiq", config.getBaseUrl());
        assertEquals("spadmin", config.getUsername());
        assertEquals("secret", config.getPassword());
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        AppConfig config = new AppConfig("https://host/identityiq/", "u", "p");
        assertEquals("https://host/identityiq", config.getBaseUrl());
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(ConfigException.class,
                () -> new AppConfig("https://host", "  ", "p"));
    }

    @Test
    void rejectsBlankPassword() {
        assertThrows(ConfigException.class,
                () -> new AppConfig("https://host", "u", ""));
    }

    @Test
    void rejectsNonHttpBaseUrl() {
        assertThrows(ConfigException.class,
                () -> new AppConfig("ftp://host", "u", "p"));
    }

    @Test
    void toStringDoesNotLeakPassword() {
        AppConfig config = new AppConfig("https://host", "u", "superSecret");
        String rendered = config.toString();
        org.junit.jupiter.api.Assertions.assertFalse(rendered.contains("superSecret"),
                "toString must not contain the password");
    }
}
