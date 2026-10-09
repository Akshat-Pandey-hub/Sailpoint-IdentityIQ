package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the three append-only Access-History repositories: the generated INSERT has exactly as many
 * {@code ?} placeholders as columns (the classic misalignment bug), the PK is a deterministic canonical
 * UUID (so an append-only re-run never duplicates → idempotent), and the record hash reacts to a
 * business-field change. Pure JDBC-string / hashing logic — no live DB, no SailPoint runtime.
 */
class NativeAccessHistoryRepositoryTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String HEX = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";

    private static String appendSql(Object repo) throws Exception {
        Field f = repo.getClass().getDeclaredField("appendSql");
        f.setAccessible(true);
        return (String) f.get(repo);
    }

    private static int columnCount(String sql) {
        int open = sql.indexOf('(');
        int close = sql.indexOf(") VALUES");
        return sql.substring(open + 1, close).split(",").length;
    }

    private static int placeholderCount(String sql) {
        int v = sql.indexOf("VALUES (");
        int end = sql.indexOf("ON CONFLICT");
        String values = sql.substring(v, end);
        int n = 0;
        for (int i = 0; i < values.length(); i++) {
            if (values.charAt(i) == '?') {
                n++;
            }
        }
        return n;
    }

    private static void assertAligned(Object repo) throws Exception {
        String sql = appendSql(repo);
        assertEquals(columnCount(sql), placeholderCount(sql),
                repo.getClass().getSimpleName() + " column/placeholder mismatch");
    }

    @Test
    void entitlementCaptureInsertIsAligned() throws Exception {
        assertAligned(new NativeHistEntitlementCaptureRepository("iiq_native", "run-1"));
    }

    @Test
    void identityEventInsertIsAligned() throws Exception {
        assertAligned(new NativeHistIdentityEventRepository("iiq_native", "run-1"));
    }

    @Test
    void certificationInsertIsAligned() throws Exception {
        assertAligned(new NativeHistCertificationRepository("iiq_native", "run-1"));
    }

    @Test
    void roleEventInsertIsAlignedAndTargetsRoleTable() throws Exception {
        NativeHistRoleEventRepository repo = new NativeHistRoleEventRepository("iiq_native", "run-1");
        assertAligned(repo);
        assertTrue(repo.targetTable().equals("iiq_native.kf_access_hist_role_event"));
        assertEquals("HistoricalRoleEvent", repo.entity());
        // role-event PK salt differs from identity-event -> no cross-table PK collision for the same source id
        assertNotEquals(NativeHistRoleEventRepository.canonicalId("not-a-uuid"),
                NativeHistIdentityEventRepository.canonicalId("not-a-uuid"));
    }

    @Test
    void canonicalIdIsDeterministicAndFallsBack() {
        String a = NativeHistEntitlementCaptureRepository.canonicalId(HEX);
        assertNotNull(a);
        assertEquals(a, UUID.fromString(a).toString());
        assertEquals(a, NativeHistEntitlementCaptureRepository.canonicalId(HEX)); // idempotent PK
        String b = NativeHistIdentityEventRepository.canonicalId("not-a-uuid");
        assertEquals(b, NativeHistIdentityEventRepository.canonicalId("not-a-uuid"));
        String c = NativeHistCertificationRepository.canonicalId("not-a-uuid");
        assertNotEquals(b, c); // per-type salting prevents cross-type PK collisions
    }

    @Test
    void recordHashReactsToBusinessChange() {
        ObjectNode base = M.createObjectNode();
        base.put("sourceId", HEX);
        base.put("attributeValue", "READ");
        ObjectNode changed = base.deepCopy();
        changed.put("attributeValue", "WRITE");
        assertNotEquals(NativeHistEntitlementCaptureRepository.recordHash(base),
                NativeHistEntitlementCaptureRepository.recordHash(changed));
    }

    @Test
    void targetTableHonoursSchema() {
        assertTrue(new NativeHistCertificationRepository("iiq_native", "r")
                .targetTable().equals("iiq_native.kf_access_hist_certification"));
    }
}
