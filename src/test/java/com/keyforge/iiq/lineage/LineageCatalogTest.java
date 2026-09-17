package com.keyforge.iiq.lineage;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void activeAccountEntitlementTableIsCovered() {
        LineageSource s = byTable("kf_account_entitlement");
        assertEquals("id", s.pk(), "kf_account_entitlement PK");
        assertEquals("derived:AccountEntitlement", s.srcObjectType(), "src_object_type");
        assertEquals("derived", s.srcInterface(), "src_interface");
        // legacy entitlementassignment entry is preserved (harmless, run-time guarded) — not broken
        assertEquals("derived:AccountEntitlement", byTable("entitlementassignment").srcObjectType());
    }

    @Test
    void catalogCoversAllActiveNormalizedDomainTables() {
        Set<String> tables = new HashSet<>();
        for (LineageSource s : LineageCatalog.all()) {
            tables.add(s.table());
        }
        // every currently active normalized domain table must carry the §6 lineage envelope
        for (String required : List.of(
                "usr", "application", "applicationinstance", "account", "entitlement",
                "kf_account_entitlement", "catalog", "usergroup", "workitem",
                "kf_role", "kf_role_hierarchy", "kf_role_entitlement", "kf_identity_role",
                "kf_identity_entitlement", "kf_object_owner", "kf_workgroup", "kf_workgroup_member",
                "kf_access_request", "kf_request_item", "kf_request_approval",
                "kf_provisioning_txn", "kf_provisioning_item", "kf_audit_event", "kf_violation",
                "kf_policy", "kf_workflow_definition", "kf_task_result", "kf_event_link",
                "kf_certification_campaign")) {
            assertTrue(tables.contains(required), "lineage catalog missing active table: " + required);
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

    private static LineageSource byTable(String table) {
        return LineageCatalog.all().stream().filter(s -> s.table().equals(table)).findFirst()
                .orElseThrow(() -> new AssertionError("no lineage catalog entry for " + table));
    }

    private static boolean notBlank(String x) {
        return x != null && !x.isBlank();
    }
}
