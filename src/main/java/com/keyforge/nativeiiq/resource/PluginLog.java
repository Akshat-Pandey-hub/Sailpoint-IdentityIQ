package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;

/**
 * The plugin's request log lines, one format for every endpoint, matching the KF Agent's own log (SERVICE/RESULT/
 * FAILED): what was requested, what came back, and why it failed. Caller-supplied text is made log-safe; record
 * contents are never logged. The level comes from the plugin's "logLevel" setting (see {@link PluginLogLevel}).
 */
final class PluginLog {

    private PluginLog() {
    }

    /** INFO: what was requested. Also applies the plugin's log-level setting first. */
    static void request(Log log, String endpoint, Object start, Object limit, String modifiedAfter) {
        PluginLogLevel.apply();
        log.info("KeyForgeNativeIIQ REQUEST  | endpoint=" + LogText.safe(endpoint)
                + " | start=" + text(start) + " | limit=" + text(limit)
                + " | modifiedAfter=" + text(modifiedAfter));
    }

    /** INFO: the successful result. */
    static void result(Log log, String endpoint, Object returned, long startedAtMillis) {
        log.info("KeyForgeNativeIIQ RESULT   | endpoint=" + LogText.safe(endpoint) + " | status=200 | returned="
                + returned + " rows | ms=" + (System.currentTimeMillis() - startedAtMillis));
    }

    /** WARN: the request was rejected as invalid (HTTP 400), with the reason sent to the caller. */
    static void rejected(Log log, String endpoint, String reason) {
        log.warn("KeyForgeNativeIIQ REJECTED | endpoint=" + LogText.safe(endpoint) + " | status=400 | reason="
                + LogText.safe(reason));
    }

    /** WARN: the caller is not allowed to read this endpoint (HTTP 403). */
    static void denied(Log log, String endpoint, String stage, Throwable cause) {
        log.warn("KeyForgeNativeIIQ DENIED   | endpoint=" + LogText.safe(endpoint) + " | status=403 | stage=" + stage
                + " | cause=" + cause(cause));
    }

    /** ERROR: the request failed inside IdentityIQ (HTTP 500); the stack trace follows the line. */
    static void failed(Log log, String endpoint, String stage, Throwable cause) {
        log.error("KeyForgeNativeIIQ FAILED   | endpoint=" + LogText.safe(endpoint) + " | status=500 | stage=" + stage
                + " | cause=" + cause(cause), cause);
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return "<not set>";
        }
        return LogText.safe(String.valueOf(value));
    }

    private static String cause(Throwable t) {
        if (t == null) {
            return "-";
        }
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + LogText.safe(t.getMessage()));
    }
}
