package com.keyforge.iiq.lineage;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure, DB-free tests for the lineage catalog and the backfill SQL builder. */
class LineageCatalogTest {

    private static final Set<String> INTERFACES = Set.of("scim", "ui-rest", "classic-rest", "classic-ui", "derived");

    @Test
    void catalogIsWellFormed() {
        List<LineageSource> all = LineageCatalog.all();
        assertFalse(all.isEmpty());
        Set<String> tables = new HashSet<>();
        for (LineageSource s : all) {
            assertTrue(notBlank(s.table()), "table");
            assertTrue(notBlank(s.pk()), "pk for " + s.table());
            assertTrue(notBlank(s.srcObjectType()), "srcObjectType for " + s.table());
            assertTrue(INTERFACES.contains(s.srcInterface()), "interface for " + s.table() + ": " + s.srcInterface());
            assertTrue(tables.add(s.table()), "duplicate table: " + s.table());
        }
    }

    @Test
    void createTableHasEnvelopeColumns() {
        String ddl = RecordLineageSql.createTable("migration_test");
        for (String col : List.of("src_system", "src_object_type", "src_object_id", "src_natural_key",
                "src_created", "src_modified", "src_event_ts", "extracted_at", "extraction_run_id",
                "src_interface", "record_hash", "raw_ref")) {
            assertTrue(ddl.contains(col), "missing column " + col);
        }
        assertTrue(ddl.contains("migration_test.kf_record_lineage"));
    }

    @Test
    void backfillFullColumnsUsesRealSources() {
        LineageSource s = new LineageSource("usr", "userid", "sailpoint.object.Identity", "scim",
                "username", "created_at", "modified_at");
        String sql = RecordLineageSql.backfill("s", s, true, true, true, true, true);
        assertTrue(sql.contains("md5('usr|' || t.userid::text)"), "deterministic lineage_id");
        assertTrue(sql.contains("COALESCE(t.source_id, t.userid::text)"), "src_object_id prefers source_id");
        assertTrue(sql.contains("t.username::text"), "natural key");
        assertTrue(sql.contains("t.created_at"), "created");
        assertTrue(sql.contains("t.modified_at"), "modified");
        assertTrue(sql.contains("md5((to_jsonb(t) - 'extracted_at')::text)"), "hash excludes extracted_at");
        assertTrue(sql.contains("'IdentityIQ'"), "src_system");
        assertTrue(sql.contains("'sailpoint.object.Identity'"), "src_object_type");
        assertTrue(sql.contains("ON CONFLICT (lineage_id) DO UPDATE"), "idempotent upsert");
    }

    @Test
    void backfillNullsUnavailableFields() {
        LineageSource s = new LineageSource("kf_event_link", "id", "derived:EventLink", "derived", null, null, null);
        String sql = RecordLineageSql.backfill("s", s, false, false, false, false, false);
        assertTrue(sql.contains("t.id::text"), "src_object_id falls back to pk when no source_id");
        assertFalse(sql.contains("COALESCE(t.source_id"), "no source_id expr");
        assertTrue(sql.contains("md5(to_jsonb(t)::text)"), "hash without extracted_at exclusion");
        assertTrue(sql.contains("now()"), "extracted_at falls back to now()");
        // natural/created/modified all NULL literals present
        assertTrue(sql.contains(", NULL, "), "null envelope fields");
    }

    private static boolean notBlank(String x) {
        return x != null && !x.isBlank();
    }
}
