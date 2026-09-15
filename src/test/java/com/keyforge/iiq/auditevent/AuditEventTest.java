package com.keyforge.iiq.auditevent;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the real {@code analyze/audit/auditDataSource.json} envelope shape (root {@code results},
 * total {@code totalCount}) and maps rows. Verifies the canonical id, the exact preservation of
 * {@code action}/{@code source}/{@code target}, the verbatim {@code created_display}, the
 * minute-precision {@code created_at} parse with the one verified pattern, a genuinely blank target
 * → NULL, and that an off-pattern timestamp maps to NULL rather than a fabricated value.
 */
class AuditEventTest {

    /** Verbatim shape captured live (with a blank-target ServerUpDown-style system event). */
    private static final String JSON =
            "{\"totalCount\":322,\"results\":["
            + "{\"action\":\"AccessRequestStart\",\"id\":\"7f0001019f061fdc819f06e6cb170029\","
            + "\"source\":\"Molly J\",\"created\":\"June 27, 2026, 2:26 AM\",\"target\":\"Alice Martin\"},"
            + "{\"action\":\"ServerUpDown\",\"id\":\"7f0001019eb91d3c819f06cf65c20836\","
            + "\"source\":\"keyforgeiga\",\"created\":\"June 27, 2026, 2:01 AM\",\"target\":\"\"}"
            + "],\"metaData\":{\"root\":\"results\",\"totalProperty\":\"totalCount\",\"id\":\"id\"}}";

    private static AuditEventService svc() {
        return new AuditEventService(null); // client unused by the pure parser
    }

    @Test
    void parsesResultsEnvelope() {
        List<AuditEvent> events = svc().parseAuditEvents(JSON);
        assertEquals(2, events.size());
        assertEquals(322, svc().parseTotal(JSON));
        AuditEvent e = events.get(0);
        assertEquals("AccessRequestStart", e.action());
        assertEquals("Molly J", e.source());
        assertEquals("Alice Martin", e.target());
        assertEquals("June 27, 2026, 2:26 AM", e.created());
    }

    @Test
    void mapsRowWithCanonicalIdRawFieldsAndParsedTimestamp() {
        AuditEvent e = svc().parseAuditEvents(JSON).get(0);
        AuditEventRow row = AuditEventRowMapper.map(e);
        assertEquals("7f000101-9f06-1fdc-819f-06e6cb170029", row.auditid());
        assertEquals("AccessRequestStart", row.action());   // preserved exactly
        assertEquals("Molly J", row.source());
        assertEquals("Alice Martin", row.target());
        assertEquals("June 27, 2026, 2:26 AM", row.createdDisplay()); // verbatim
        assertEquals(LocalDateTime.of(2026, 6, 27, 2, 26), row.createdAt()); // minute precision, no seconds/tz
    }

    @Test
    void blankTargetBecomesNull() {
        AuditEvent e = svc().parseAuditEvents(JSON).get(1); // ServerUpDown with target ""
        assertNull(e.target());                              // parser blanks empty strings
        AuditEventRow row = AuditEventRowMapper.map(e);
        assertNull(row.target());
        assertEquals("ServerUpDown", row.action());
    }

    @Test
    void timestampParsingIsMinutePrecisionAmPm() {
        assertEquals(LocalDateTime.of(2026, 7, 27, 19, 17),
                AuditEventRowMapper.parseCreated("July 27, 2026, 7:17 PM"));
        assertEquals(LocalDateTime.of(2026, 6, 28, 22, 35),
                AuditEventRowMapper.parseCreated("June 28, 2026, 10:35 PM"));
    }

    @Test
    void offPatternTimestampMapsToNullNeverFabricated() {
        assertNull(AuditEventRowMapper.parseCreated("2026-06-27T02:26:31Z")); // ISO, not the display format
        assertNull(AuditEventRowMapper.parseCreated(null));
        assertNull(AuditEventRowMapper.parseCreated("   "));
    }

    @Test
    void missingIdIsReportedNotInvented() {
        AuditEventMappingException ex = assertThrows(AuditEventMappingException.class,
                () -> AuditEventRowMapper.map(new AuditEvent(null, "Act", "src", "tgt", "June 27, 2026, 2:26 AM")));
        assertTrue(ex.getMessage().toLowerCase().contains("id"));
    }

    @Test
    void duplicateSourceIdsShareTheSameCanonicalDedupKey() {
        // Persistence de-duplicates by canonical auditid; two rows with the same source id must
        // resolve to the same key so the duplicate is detected deterministically.
        String key1 = AuditEventRowMapper.toCanonicalUuid("7f0001019f061fdc819f06e6cb170029");
        String key2 = AuditEventRowMapper.toCanonicalUuid("7f0001019f061fdc819f06e6cb170029");
        assertEquals(key1, key2);
        assertEquals("7f000101-9f06-1fdc-819f-06e6cb170029", key1);
    }

    @Test
    void emptyEnvelopeYieldsNoEvents() {
        assertTrue(svc().parseAuditEvents("{\"totalCount\":0,\"results\":[]}").isEmpty());
        assertEquals(0, svc().parseTotal("{\"totalCount\":0,\"results\":[]}"));
    }
}
