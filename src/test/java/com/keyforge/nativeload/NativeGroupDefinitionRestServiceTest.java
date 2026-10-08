package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for the KF Agent Group-Definition read service: reuses the EXISTING native import over a fake page
 * source (no live IIQ, no DB — proving the REST path never needs PostgreSQL), returns our 15 DB-named business
 * fields (verified field-by-field against the repository upsert bindings — every non-lineage column is a
 * direct native value, no KeyForge-derived columns), excludes the groupid PK + record_hash + lineage, keeps
 * the four booleans + three ISO timestamps native, preserves null, and supports generic exact filtering on
 * every scalar field. Covers a Group and a Population row.
 */
class NativeGroupDefinitionRestServiceTest {

    private final NativeGroupDefinitionRestService svc = new NativeGroupDefinitionRestService();

    private static final String GID = "7f00010198421229819849c815b90bfc";
    private static final String PID = "7f00010198421229819849c815b90ddd";

    /** A factory-backed GROUP: factory_id/name set, private, indexed, with an owner + refresh timestamp. */
    private static String group() {
        return "{\"sourceId\":\"" + GID + "\",\"name\":\"EntraAuth-groups\",\"type\":\"GROUP\","
                + "\"factoryId\":\"7f0001fac7019849c8\",\"factoryName\":\"EntraAuth Group Factory\","
                + "\"filterExpression\":\"application.name==\\\"EntraAuth\\\"\","
                + "\"isPrivate\":true,\"indexed\":true,\"nullGroup\":false,\"nameUnique\":true,"
                + "\"ownerId\":\"7f00owner01\",\"ownerName\":\"spadmin\","
                + "\"lastRefresh\":\"2026-10-05T00:00:00Z\",\"created\":\"2025-05-28T00:00:00Z\","
                + "\"modified\":\"2026-10-05T00:00:00Z\","
                // lineage/technical — must NOT appear in the response
                + "\"srcSystem\":\"IdentityIQ\",\"srcInterface\":\"native_iiq_java_api\","
                + "\"srcObjectType\":\"sailpoint.object.GroupDefinition\",\"extractionRunId\":\"run-1\","
                + "\"extractedAt\":\"2026-10-05T15:49:44.611Z\"}";
    }

    /** A POPULATION: no factory, not private/indexed; optional owner/refresh null to prove null preservation. */
    private static String population() {
        return "{\"sourceId\":\"" + PID + "\",\"name\":\"Active Employees\",\"type\":\"POPULATION\","
                + "\"filterExpression\":\"inactive==false\","
                + "\"isPrivate\":false,\"indexed\":false,\"nullGroup\":false,\"nameUnique\":true,"
                + "\"created\":\"2025-06-01T00:00:00Z\",\"modified\":\"2025-06-02T00:00:00Z\"}";
    }

    private static NativeGroupDefinitionPageSource source() {
        return (start, limit) -> start == 0 ? "{\"rows\":[" + group() + "," + population() + "]}" : "{\"rows\":[]}";
    }

    private static NativeGroupDefinitionPageSource emptySource() {
        return (start, limit) -> "{\"rows\":[]}";
    }

    private static Map<String, String> f(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void returnsFifteenFieldsAndExcludesKeyforgeFields() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        assertEquals(2, rows.size());

        Map<String, Object> r = rows.get(0);
        assertEquals(GID, r.get("source_id"));
        assertEquals("EntraAuth-groups", r.get("name"));
        assertEquals("GROUP", r.get("type"));
        assertEquals("7f0001fac7019849c8", r.get("factory_id"));
        assertEquals("EntraAuth Group Factory", r.get("factory_name"));
        assertEquals("application.name==\"EntraAuth\"", r.get("filter_expression"));
        assertEquals("spadmin", r.get("owner_name"));
        assertEquals(15, r.size(), "exactly the 15 SailPoint-facing business fields");

        assertFalse(r.containsKey("groupid"));          // KeyForge canonical UUID PK
        assertFalse(r.containsKey("record_hash"));
        assertFalse(r.containsKey("source_system"));
        assertFalse(r.containsKey("source_interface"));
        assertFalse(r.containsKey("source_object_type"));
        assertFalse(r.containsKey("extraction_run_id"));
        assertFalse(r.containsKey("extracted_at"));
    }

    @Test
    void booleanTimestampTypesAndNullsPreserved() {
        List<Map<String, Object>> rows = svc.fetch(source(), null, null, null);
        Map<String, Object> g = rows.get(0);

        // booleans stay booleans
        assertEquals(Boolean.TRUE, g.get("is_private"));
        assertEquals(Boolean.TRUE, g.get("indexed"));
        assertEquals(Boolean.FALSE, g.get("null_group"));
        assertEquals(Boolean.TRUE, g.get("name_unique"));
        // timestamps serialize as ISO strings
        assertEquals("2026-10-05T00:00:00Z", g.get("last_refresh"));
        assertEquals("2025-05-28T00:00:00Z", g.get("created_at"));
        assertEquals("2026-10-05T00:00:00Z", g.get("modified_at"));

        // population: factory + owner + last_refresh null, booleans false
        Map<String, Object> p = rows.get(1);
        assertEquals("POPULATION", p.get("type"));
        assertEquals(Boolean.FALSE, p.get("is_private"));
        assertEquals(Boolean.FALSE, p.get("indexed"));
        assertNull(p.get("factory_id"), "population has no factory");
        assertNull(p.get("factory_name"));
        assertNull(p.get("owner_id"));
        assertNull(p.get("owner_name"));
        assertNull(p.get("last_refresh"));
    }

    @Test
    void genericScalarBooleanFilters() {
        assertEquals(1, svc.fetch(source(), f("source_id", GID), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name", "Active Employees"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "GROUP"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("type", "POPULATION"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("factory_name", "EntraAuth Group Factory"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("is_private", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("is_private", "false"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("indexed", "true"), null, null).size());
        assertEquals(2, svc.fetch(source(), f("name_unique", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "group"), null, null).size(), "case-sensitive");
    }

    @Test
    void multipleFiltersAndCombine() {
        assertEquals(1, svc.fetch(source(), f("type", "GROUP", "is_private", "true"), null, null).size());
        assertEquals(0, svc.fetch(source(), f("type", "GROUP", "is_private", "false"), null, null).size());
    }

    @Test
    void unknownAndTechnicalFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("nope", "x"), null, null));
        // technical columns are not exposed and therefore not filterable
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("groupid", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("record_hash", "x"), null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.fetch(source(), f("extraction_run_id", "x"), null, null));
    }

    @Test
    void defaultReturnsAllAndWindowAppliesAfterFiltering() {
        assertEquals(2, svc.fetch(source(), null, null, null).size());
        List<Map<String, Object>> win = svc.fetch(source(), null, 1, 1);
        assertEquals(1, win.size());
        assertEquals("Active Employees", win.get(0).get("name"));
        // filter first, then window
        assertEquals(2, svc.fetch(source(), f("name_unique", "true"), null, null).size());
        assertEquals(1, svc.fetch(source(), f("name_unique", "true"), 1, 5).size());
    }

    @Test
    void emptySourceReturnsEmptyArray() {
        assertEquals(List.of(), svc.fetch(emptySource(), null, null, null));
    }
}
