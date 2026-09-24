package com.keyforge.iiq.nativeevent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The native kf_event_link table must be schema-qualified and strictly append-only. */
class NativeEventLinkRepositoryTest {

    private static final String T = "iiq_native.kf_event_link";

    @Test
    void ddlIsSchemaQualifiedWithLinkIdPk() {
        String ddl = NativeEventLinkRepository.createTableSql(T);
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS iiq_native.kf_event_link"), ddl);
        assertTrue(ddl.contains("link_id uuid PRIMARY KEY"), ddl);
        assertTrue(ddl.contains("target_object_id text"), ddl);
        assertTrue(ddl.contains("link_type text"), ddl);
    }

    @Test
    void appendIsInsertOnlyOnConflictDoNothing() {
        String sql = NativeEventLinkRepository.appendSql(T);
        assertTrue(sql.startsWith("INSERT INTO iiq_native.kf_event_link"), sql);
        assertTrue(sql.contains("ON CONFLICT (link_id) DO NOTHING"), sql);
        assertTrue(!sql.toUpperCase().contains("UPDATE"), "append-only: no UPDATE");
        assertTrue(!sql.toUpperCase().contains("DELETE"), "append-only: no DELETE");
    }
}
