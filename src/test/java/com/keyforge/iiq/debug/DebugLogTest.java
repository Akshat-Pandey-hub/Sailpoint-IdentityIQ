package com.keyforge.iiq.debug;

import com.keyforge.iiq.client.IiqApiException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plain-language reason attached to each kind of error. */
class DebugLogTest {

    private static String reasonFor(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return DebugLog.reason(t, root);
    }

    @Test
    void explainsIdentityIqFailures() {
        assertTrue(reasonFor(new IiqApiException("IdentityIQ login failed for user 'x' (HTTP 200)", 200))
                .startsWith("IIQ_LOGIN_REJECTED"));
        assertTrue(reasonFor(new IiqApiException("IdentityIQ redirected https://h/x to /login.jsf — the authenticated"
                + " session was not accepted (HTTP 302).", 302)).startsWith("IIQ_SESSION_REJECTED"));
        assertTrue(reasonFor(new IiqApiException("I/O error calling IdentityIQ at h",
                new RuntimeException(new ConnectException("refused")))).startsWith("IIQ_UNREACHABLE"));
        assertTrue(reasonFor(new IiqApiException("I/O error calling IdentityIQ at h",
                new UnknownHostException("h"))).startsWith("IIQ_HOST_UNKNOWN"));
        assertTrue(reasonFor(new IiqApiException("IdentityIQ request to h failed with HTTP 404: x", 404))
                .startsWith("IIQ_NOT_FOUND"));
        assertTrue(reasonFor(new IiqApiException("IdentityIQ request to h failed with HTTP 500: x", 500))
                .startsWith("IIQ_SERVER_ERROR"));
    }

    @Test
    void neverPrintsTheIiqPassword() {
        for (DebugLog.Level level : new DebugLog.Level[]{DebugLog.Level.DEBUG, DebugLog.Level.NORMAL}) {
            String line = DebugLog.render(level, "IIQ-LOGIN", false, "step", "result", "IIQ_USERNAME", "spadmin",
                    "IIQ_PASSWORD", "Secret@123");
            assertTrue(line.contains("IIQ_PASSWORD=" + DebugLog.HIDDEN), level + ": " + line);
            assertFalse(line.contains("Secret@123"), level + ": " + line);
        }
    }

    @Test
    void debugShowsCredentialsNormalDoesNot() {
        Object[] kv = {"scheme", "Basic", "sentUser", "client-a", "sentPassword", "pw-a", "expectedUser", "client-b",
                "expectedPassword", "pw-b", "result", "DENY 401", "reason", "WRONG_USERNAME"};
        String debug = DebugLog.render(DebugLog.Level.DEBUG, "AUTH", false, kv);
        assertTrue(debug.contains("AUTH") && debug.contains("sentUser=client-a")
                && debug.contains("expectedPassword=pw-b"), debug);

        String normal = DebugLog.render(DebugLog.Level.NORMAL, "AUTH", true, kv); // a rejection is always shown
        assertTrue(normal.contains("AUTH") && normal.contains("result=DENY 401")
                && normal.contains("reason=WRONG_USERNAME"), normal);
        assertFalse(normal.contains("client-a") || normal.contains("pw-a") || normal.contains("pw-b"), normal);
    }

    @Test
    void normalDropsDetailLinesAndOffDropsEverything() {
        assertNull(DebugLog.render(DebugLog.Level.NORMAL, "SETTING", false, "name", "IIQ_USERNAME", "value", "spadmin"));
        assertNull(DebugLog.render(DebugLog.Level.NORMAL, "REQUEST", false, "path", "/health"));
        assertNull(DebugLog.render(DebugLog.Level.NORMAL, "IIQ-CALL", false, "url", "https://h/x"));
        assertNull(DebugLog.render(DebugLog.Level.NORMAL, "IIQ-LOGIN", false, "step", "start"));
        assertNotNull(DebugLog.render(DebugLog.Level.NORMAL, "RESULT", false, "status", 200));
        assertNull(DebugLog.render(DebugLog.Level.NORMAL, "AUTH", false, "result", "ALLOW"), "accepted login: debug only");
        assertNull(DebugLog.render(DebugLog.Level.OFF, "SERVICE", false, "endpoint", "/x"), "off: no routine lines");
        assertNotNull(DebugLog.render(DebugLog.Level.OFF, "FAILED", true, "what", "x"), "off: failures still shown");
        assertNotNull(DebugLog.render(DebugLog.Level.OFF, "AUTH", true, "result", "DENY 401"), "off: 401 reason shown");
    }

    @Test
    void parsesTheLevelSetting() {
        assertEquals(DebugLog.Level.NORMAL, DebugLog.parseLevel(null, null), "default is normal, never debug");
        assertEquals(DebugLog.Level.NORMAL, DebugLog.parseLevel("", null));
        assertEquals(DebugLog.Level.NORMAL, DebugLog.parseLevel("verbose-typo", null), "unknown -> normal");
        assertEquals(DebugLog.Level.DEBUG, DebugLog.parseLevel(null, "true"), "legacy KF_DEBUG=true");
        assertEquals(DebugLog.Level.OFF, DebugLog.parseLevel("quiet", null));
        assertEquals(DebugLog.Level.OFF, DebugLog.parseLevel("error", null));
        assertEquals(DebugLog.Level.NORMAL, DebugLog.parseLevel("normal", null));
        assertEquals(DebugLog.Level.NORMAL, DebugLog.parseLevel(" NORMAL ", null));
        assertEquals(DebugLog.Level.DEBUG, DebugLog.parseLevel("debug", "false"));
        assertEquals(DebugLog.Level.OFF, DebugLog.parseLevel("off", null));
        assertEquals(DebugLog.Level.OFF, DebugLog.parseLevel(null, "false"));
    }

    @Test
    void explainsOtherFailures() {
        assertTrue(reasonFor(new SQLException("connection refused")).startsWith("POSTGRES_ERROR"));
        assertTrue(reasonFor(new IllegalArgumentException("unknown filter")).startsWith("BAD_INPUT"));
        assertTrue(reasonFor(new IllegalStateException("x")).startsWith("UNEXPECTED"));
    }
}
