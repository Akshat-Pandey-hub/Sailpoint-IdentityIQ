package com.keyforge.iiq.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an IdentityIQ error response may contribute to an exception message (and so to ERROR logs): IIQ's own short
 * error text, never the raw body. All bodies here are fake.
 */
class IiqErrorSummaryTest {

    @Test
    void keepsIiqsOwnShortErrorText() {
        assertEquals("IdentityIQ says: Plugin endpoint not found",
                IiqApiClient.errorSummary("{\"status\":404,\"message\":\"Plugin endpoint not found\"}"));
        assertEquals("IdentityIQ says: Missing right SystemAdministrator",
                IiqApiClient.errorSummary("{\"status\":403,\"error\":{\"type\":\"x\",\"message\":\"Missing right SystemAdministrator\"}}"));
    }

    @Test
    void neverCopiesARawBodyIntoTheMessage() {
        String html = "<html><body>identity row alice@example.com fake-sensitive-data</body></html>";
        String summary = IiqApiClient.errorSummary(html);
        assertFalse(summary.contains("fake-sensitive-data") || summary.contains("alice@example.com"), summary);
        assertTrue(summary.contains(html.length() + " chars"), summary);

        String jsonData = "{\"rows\":[{\"name\":\"alice\",\"email\":\"alice@example.com\"}]}";
        assertFalse(IiqApiClient.errorSummary(jsonData).contains("alice"), "JSON without an error text: length only");
    }

    @Test
    void capsLongErrorTextAndHandlesEmptyBodies() {
        String longText = "x".repeat(1000);
        String summary = IiqApiClient.errorSummary("{\"message\":\"" + longText + "\"}");
        assertTrue(summary.length() < 260, "capped: " + summary.length());
        assertEquals("empty response body", IiqApiClient.errorSummary(""));
        assertEquals("empty response body", IiqApiClient.errorSummary(null));
    }
}
