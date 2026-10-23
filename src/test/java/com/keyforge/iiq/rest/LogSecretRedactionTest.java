package com.keyforge.iiq.rest;

import com.keyforge.iiq.debug.DebugLog;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What actually reaches the log through SLF4J/Log4j2 at DEBUG: the IdentityIQ password, Bearer tokens and the raw
 * Basic Auth header value are never written; the auth decision and its reason are.
 */
class LogSecretRedactionTest {

    private static final String IIQ_PASSWORD = "Iiq-Secret-Value-42";
    private static final String BEARER = "eyJhbGciOiJIUzI1NiJ9.token-body.signature";

    private final List<String> lines = new ArrayList<>();
    private AbstractAppender capture;
    private Level previous;

    @BeforeEach
    void captureEverythingAtDebug() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = ctx.getConfiguration().getRootLogger();
        previous = root.getLevel();
        capture = new AbstractAppender("capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                lines.add(event.getMessage().getFormattedMessage());
            }
        };
        capture.start();
        root.addAppender(capture, Level.ALL, null);
        root.setLevel(Level.DEBUG);
        ctx.updateLoggers();
    }

    @AfterEach
    void stopCapturing() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = ctx.getConfiguration().getRootLogger();
        root.removeAppender("capture");
        root.setLevel(previous);
        ctx.updateLoggers();
        capture.stop();
    }

    private String all() {
        return String.join("\n", lines);
    }

    @Test
    void iiqPasswordIsNeverWritten() {
        DebugLog.log("IIQ-LOGIN", "step", "start", "IIQ_USERNAME", "svc-user", "IIQ_PASSWORD", IIQ_PASSWORD);
        DebugLog.log("SETTING", "name", "IIQ_PASSWORD", "IIQ_PASSWORD", IIQ_PASSWORD);

        assertFalse(lines.isEmpty(), "lines were written at debug");
        assertFalse(all().contains(IIQ_PASSWORD), all());
        assertTrue(all().contains("IIQ_PASSWORD=****** (hidden)"), all());
    }

    @Test
    void bearerTokenIsNeverWritten() {
        RestAuthFilter filter = RestAuthFilter.forClientCredentials("client", "secret");
        String header = "Bearer " + BEARER;
        filter.logAuth(header, filter.denyReason(header));

        assertTrue(all().contains("NOT_BASIC"), all());
        assertFalse(all().contains(BEARER), all());
        assertFalse(all().contains("token-body"), all());
    }

    @Test
    void rawBasicAuthHeaderValueIsNeverWritten() {
        RestAuthFilter filter = RestAuthFilter.forClientCredentials("client", "secret");
        String encoded = Base64.getEncoder().encodeToString("client:wrong".getBytes(StandardCharsets.UTF_8));
        String header = "Basic " + encoded;
        filter.logAuth(header, filter.denyReason(header));

        assertTrue(all().contains("WRONG_PASSWORD"), all());
        assertFalse(all().contains(encoded), "the encoded header value is never logged: " + all());
    }
}
