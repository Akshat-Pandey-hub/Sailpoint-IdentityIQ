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

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What actually reaches the log through SLF4J/Log4j2 (all values here are fake). Rejections and failures are logged
 * once, with their reason, at every level; the IdentityIQ password, Bearer tokens and the raw Basic Auth header
 * value never appear.
 */
class LogSecretRedactionTest {

    private static final String FAKE_IIQ_PASSWORD = "fake-iiq-password-42";
    private static final String FAKE_BEARER = "eyJhbGciOiJIUzI1NiJ9.fake-body.fake-signature";

    private final List<LogEvent> events = new ArrayList<>();
    private AbstractAppender capture;
    private Level previous;

    @BeforeEach
    void captureEverything() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = ctx.getConfiguration().getRootLogger();
        previous = root.getLevel();
        capture = new AbstractAppender("capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
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
        StringBuilder sb = new StringBuilder();
        for (LogEvent e : events) {
            sb.append(e.getMessage().getFormattedMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void aRejectedRequestIsOneWarningWithItsReason() {
        RestAuthFilter filter = RestAuthFilter.forClientCredentials("client", "secret");
        String header = "Basic " + Base64.getEncoder().encodeToString("client:wrong".getBytes(StandardCharsets.UTF_8));
        filter.logAuth(header, filter.denyReason(header), "GET /kfagent/identities", "/10.0.0.5");

        assertEquals(1, events.size(), "exactly one line for a 401: " + all());
        assertEquals(Level.WARN, events.get(0).getLevel());
        String line = all();
        assertTrue(line.contains("DENY 401") && line.contains("WRONG_PASSWORD")
                && line.contains("GET /kfagent/identities") && line.contains("/10.0.0.5"), line);
    }

    @Test
    void bearerTokenAndRawBasicHeaderAreNeverWritten() {
        RestAuthFilter filter = RestAuthFilter.forClientCredentials("client", "secret");
        String bearer = "Bearer " + FAKE_BEARER;
        filter.logAuth(bearer, filter.denyReason(bearer), "GET /health", "/127.0.0.1");
        String encoded = Base64.getEncoder().encodeToString("client:wrong".getBytes(StandardCharsets.UTF_8));
        filter.logAuth("Basic " + encoded, filter.denyReason("Basic " + encoded), "GET /health", "/127.0.0.1");

        assertTrue(all().contains("NOT_BASIC"), all());
        assertFalse(all().contains(FAKE_BEARER) || all().contains("fake-body"), all());
        assertFalse(all().contains(encoded), "the encoded header value is never logged: " + all());
    }

    @Test
    void aFailureIsOneErrorLineWithReasonAndNeverTheIiqPassword() {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("test.failure");
        Exception e = new com.keyforge.iiq.client.IiqApiException("I/O error calling IdentityIQ at http://iiq/login.jsf",
                new ConnectException("Connection refused"));
        DebugLog.failure(log, "GET /kfagent/identities failed (caller gets HTTP 500)", e,
                "IIQ_PASSWORD", FAKE_IIQ_PASSWORD);

        assertEquals(1, events.size(), "exactly one line per failure: " + all());
        LogEvent event = events.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        String line = all();
        assertTrue(line.contains("reason=IIQ_UNREACHABLE") && line.contains("at=") && line.contains("cause="), line);
        assertFalse(line.contains(FAKE_IIQ_PASSWORD), line);
        assertTrue(line.contains("IIQ_PASSWORD=****** (hidden)"), line);
        assertNull(event.getThrown(), "a known cause needs no stack trace at normal level");
    }

    @Test
    void aPluginFailureShowsTheReasonIdentityIqGave() {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("test.failure");
        Exception e = new com.keyforge.iiq.client.IiqApiException("IdentityIQ request to "
                + "http://iiq/plugin/rest/keyForgeNativeIIQ/roles?start=0&limit=3 failed with HTTP 500: "
                + "IdentityIQ says: could not resolve property: foo of: sailpoint.object.Bundle", 500);
        DebugLog.failure(log, "GET /kfagent/roles failed (caller gets HTTP 500)", e);

        assertEquals(1, events.size(), "exactly one line per failure: " + all());
        String line = all();
        assertTrue(line.contains("reason=IIQ_SERVER_ERROR"), line);
        assertTrue(line.contains("IdentityIQ says: could not resolve property: foo"), line);
    }

    @Test
    void anUnexpectedFailureKeepsItsStackTrace() {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("test.failure");
        DebugLog.failure(log, "Unexpected error: boom", new IllegalStateException("boom"));

        assertEquals(1, events.size(), all());
        assertTrue(all().contains("reason=UNEXPECTED"), all());
        assertNotNull(events.get(0).getThrown(), "unexpected errors carry the stack trace");
    }

    @Test
    void aRecoverableProblemIsOneWarning() {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("test.failure");
        DebugLog.warning(log, "Soft-delete sweep [kf_identity] failed", new java.sql.SQLException("lock timeout"));

        assertEquals(1, events.size(), all());
        assertEquals(Level.WARN, events.get(0).getLevel());
        assertTrue(all().contains("reason=POSTGRES_ERROR"), all());
    }
}
