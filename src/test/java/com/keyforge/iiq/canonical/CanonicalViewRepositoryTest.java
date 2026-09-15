package com.keyforge.iiq.canonical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the remaining canonical artifact — the derived {@code kf_identity_account} view. The four
 * core entities are physical tables (produced by the persistence path), so no core views are built
 * here. The view is additive (CREATE OR REPLACE VIEW, never a table create/drop/rename), reads the
 * real physical columns ({@code userid}/{@code accountid}), preserves both raw ids and the
 * resolution status, and can attach to either the canonical or the legacy physical tables.
 */
class CanonicalViewRepositoryTest {

    private static CanonicalViewRepository repo(String schema) {
        return new CanonicalViewRepository(schema);
    }

    @Test
    void identityAccountViewIsAdditiveNeverATableMutation() {
        String sql = repo("acme_prod").identityAccountViewSql("kf_account", "kf_identity");
        assertTrue(sql.startsWith("CREATE OR REPLACE VIEW acme_prod.kf_identity_account AS"), sql);
        assertFalse(sql.contains("DROP "), sql);
        assertFalse(sql.contains("CREATE TABLE"), sql);
        assertFalse(sql.contains("ALTER TABLE"), sql);
        assertFalse(sql.contains("DELETE "), sql);
    }

    @Test
    void identityAccountPreservesRawIdsAndResolutionStatusOverCanonicalTables() {
        String sql = repo("migration_test").identityAccountViewSql("kf_account", "kf_identity");
        assertTrue(sql.contains("ac.userid AS identity_id"), sql);
        assertTrue(sql.contains("ac.accountid AS account_id"), sql);
        assertTrue(sql.contains("'NO_IDENTITY_REF'"), sql);
        assertTrue(sql.contains("'UNRESOLVED'"), sql);
        assertTrue(sql.contains("'RESOLVED'"), sql);
        assertTrue(sql.contains("FROM migration_test.kf_account ac"), sql);
        assertTrue(sql.contains("LEFT JOIN migration_test.kf_identity u ON u.userid = ac.userid"), sql);
    }

    @Test
    void identityAccountAlsoBuildsOverLegacyPhysicalTables() {
        String sql = repo("iiq_migration_test").identityAccountViewSql("account", "usr");
        assertTrue(sql.contains("FROM iiq_migration_test.account ac"), sql);
        assertTrue(sql.contains("LEFT JOIN iiq_migration_test.usr u ON u.userid = ac.userid"), sql);
    }

    @Test
    void schemaNameIsValidatedAndHonoured() {
        assertEquals("acme_prod.kf_identity_account",
                repo("acme_prod").qualified(CanonicalViewRepository.V_IDENTITY_ACCOUNT));
        assertEquals(CanonicalViewRepository.DEFAULT_SCHEMA, new CanonicalViewRepository().schema());
    }

    @Test
    void coreCanonicalNamesAreTheFourPdfEntities() {
        assertEquals(4, CanonicalViewRepository.CORE_CANONICAL.length);
        assertTrue(java.util.List.of(CanonicalViewRepository.CORE_CANONICAL)
                .containsAll(java.util.List.of("kf_identity", "kf_account", "kf_application", "kf_entitlement")));
    }
}
