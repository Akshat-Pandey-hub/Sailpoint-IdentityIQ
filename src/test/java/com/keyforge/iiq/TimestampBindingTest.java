package com.keyforge.iiq;

import com.keyforge.iiq.taskresult.TaskResult;
import com.keyforge.iiq.taskresult.TaskResultRepository;
import com.keyforge.iiq.taskresult.TaskResultRowMapper;
import com.keyforge.iiq.workflow.WorkflowDefinition;
import com.keyforge.iiq.workflow.WorkflowRepository;
import com.keyforge.iiq.workflow.WorkflowRowMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the timestamp-correctness fix at the repository binding layer: a UTC ("…Z") source
 * timestamp is bound to a {@code timestamptz} column as an explicit UTC {@link OffsetDateTime}
 * representing the <b>same instant</b> — not a zone-less {@code LocalDateTime} that PostgreSQL would
 * reinterpret in the session timezone. Uses JDBC dynamic proxies to capture the actual value passed
 * to {@code PreparedStatement.setObject}; no database is required.
 */
class TimestampBindingTest {

    /** Captures every setObject(index, value) the repository binds. */
    private static final class Capture {
        final List<Object> boundObjects = new ArrayList<>();
    }

    private static Connection fakeConnection(Capture cap) {
        InvocationHandler rsH = (proxy, method, args) -> switch (method.getName()) {
            case "next" -> Boolean.TRUE;            // one row
            case "getBoolean" -> Boolean.TRUE;      // (xmax = 0) => inserted
            case "close" -> null;
            default -> defaultValue(method);
        };
        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                TimestampBindingTest.class.getClassLoader(), new Class[] {ResultSet.class}, rsH);

        InvocationHandler psH = (proxy, method, args) -> {
            switch (method.getName()) {
                case "setObject" -> cap.boundObjects.add(args[1]);
                case "executeQuery" -> { return rs; }
                case "close" -> { return null; }
                default -> { }
            }
            return defaultValue(method);
        };
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(
                TimestampBindingTest.class.getClassLoader(), new Class[] {PreparedStatement.class}, psH);

        InvocationHandler connH = (proxy, method, args) ->
                method.getName().equals("prepareStatement") ? ps : defaultValue(method);
        return (Connection) Proxy.newProxyInstance(
                TimestampBindingTest.class.getClassLoader(), new Class[] {Connection.class}, connH);
    }

    private static Object defaultValue(Method m) {
        Class<?> r = m.getReturnType();
        if (r == boolean.class) return Boolean.FALSE;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        return null;
    }

    @Test
    void taskResultUtcTimestampsBindAsSameInstant() throws Exception {
        // SCIM source values end in 'Z' (UTC).
        TaskResult t = new TaskResult("7f0001019842122981984a0a088a0e80", "Full Text Index Refresh",
                "System", "Full Text Index Refresh", "Error", "keyforgeiga", "spadmin",
                "2025-07-27T04:00:28.298Z", "2025-07-27T04:00:28.321Z",
                false, false, 0, null);
        Capture cap = new Capture();
        new TaskResultRepository("s").upsert(fakeConnection(cap), TaskResultRowMapper.map(t));

        // Only launched + completed are bound via setObject; both must be OffsetDateTime at UTC.
        assertEquals(2, cap.boundObjects.size());
        assertSameInstant(Instant.parse("2025-07-27T04:00:28.298Z"), cap.boundObjects.get(0));
        assertSameInstant(Instant.parse("2025-07-27T04:00:28.321Z"), cap.boundObjects.get(1));
    }

    @Test
    void workflowUtcTimestampsBindAsSameInstant() throws Exception {
        WorkflowDefinition wf = new WorkflowDefinition("7f0001019714166881971476886b018c",
                "Do Provisioning Forms", "Subprocess", "handler.Class", "desc",
                "2025-05-28T01:16:41.963Z", "2025-05-28T01:17:13.253Z");
        Capture cap = new Capture();
        new WorkflowRepository("s").upsert(fakeConnection(cap), WorkflowRowMapper.map(wf));

        assertEquals(2, cap.boundObjects.size());
        assertSameInstant(Instant.parse("2025-05-28T01:16:41.963Z"), cap.boundObjects.get(0));
        assertSameInstant(Instant.parse("2025-05-28T01:17:13.253Z"), cap.boundObjects.get(1));
    }

    /** The bound value is an OffsetDateTime whose instant equals the source, independent of default TZ. */
    private static void assertSameInstant(Instant expected, Object bound) {
        assertTrue(bound instanceof OffsetDateTime,
                "timestamptz binding must be OffsetDateTime (UTC), was " + (bound == null ? "null" : bound.getClass()));
        OffsetDateTime odt = (OffsetDateTime) bound;
        assertEquals(ZoneOffset.UTC, odt.getOffset(), "must be bound at UTC offset");
        assertEquals(expected, odt.toInstant(), "stored instant must equal the source '…Z' instant");
        // Guard against the old bug: the zone-less local wall-clock, if reinterpreted in a non-UTC
        // session, would be a different instant — here the offset is pinned to UTC so it cannot drift.
        assertFalse(odt.getOffset().getTotalSeconds() != 0, "offset must be exactly UTC (no session drift)");
    }
}
