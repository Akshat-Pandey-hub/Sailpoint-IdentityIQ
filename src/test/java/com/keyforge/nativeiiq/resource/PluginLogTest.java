package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLogTest {

    private final List<String> lines = new ArrayList<String>();
    private final List<Object> thrown = new ArrayList<Object>();

    /** A Log that records "LEVEL message" for info/warn/error calls. */
    private Log recordingLog() {
        return (Log) Proxy.newProxyInstance(Log.class.getClassLoader(), new Class<?>[]{Log.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("is")) {
                        return Boolean.TRUE;
                    }
                    if (args != null && args.length > 0) {
                        lines.add(name.toUpperCase() + " " + args[0]);
                        thrown.add(args.length > 1 ? args[1] : null);
                    }
                    return null;
                });
    }

    @Test
    void requestAndResultSayWhatWasAskedAndWhatCameBack() {
        Log log = recordingLog();
        PluginLog.request(log, "roles", 0, 100, null);
        PluginLog.result(log, "roles", 14, System.currentTimeMillis());

        assertEquals("INFO KeyForgeNativeIIQ REQUEST  | endpoint=roles | start=0 | limit=100 | modifiedAfter=<not set>",
                lines.get(0));
        assertTrue(lines.get(1).startsWith("INFO KeyForgeNativeIIQ RESULT   | endpoint=roles | status=200 | returned=14 rows | ms="),
                lines.get(1));
    }

    @Test
    void rejectedDeniedAndFailedCarryTheirReason() {
        Log log = recordingLog();
        PluginLog.rejected(log, "roles", "invalid 'modifiedAfter'");
        PluginLog.denied(log, "roles", "getContext", new SecurityException("not allowed"));
        IllegalStateException boom = new IllegalStateException("could not resolve property: foo");
        PluginLog.failed(log, "roles", "extract", boom);

        assertEquals("WARN KeyForgeNativeIIQ REJECTED | endpoint=roles | status=400 | reason=invalid 'modifiedAfter'",
                lines.get(0));
        assertEquals("WARN KeyForgeNativeIIQ DENIED   | endpoint=roles | status=403 | stage=getContext"
                + " | cause=SecurityException: not allowed", lines.get(1));
        assertEquals("ERROR KeyForgeNativeIIQ FAILED   | endpoint=roles | status=500 | stage=extract"
                + " | cause=IllegalStateException: could not resolve property: foo", lines.get(2));
        assertNotNull(thrown.get(2), "a failure keeps its stack trace");
    }

    @Test
    void callerTextCannotBreakTheLogLine() {
        Log log = recordingLog();
        PluginLog.request(log, "roles", 0, 10, "2026\nERROR forged");
        assertTrue(lines.get(0).endsWith("modifiedAfter=2026_ERROR forged"), lines.get(0));
    }
}
