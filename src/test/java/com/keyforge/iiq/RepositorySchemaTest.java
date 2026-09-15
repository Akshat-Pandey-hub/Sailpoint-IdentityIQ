package com.keyforge.iiq;

import com.keyforge.iiq.account.AccountRepository;
import com.keyforge.iiq.application.ApplicationInstanceRepository;
import com.keyforge.iiq.application.ApplicationRepository;
import com.keyforge.iiq.assignment.EntitlementAssignmentRepository;
import com.keyforge.iiq.catalog.CatalogRepository;
import com.keyforge.iiq.entitlement.EntitlementRepository;
import com.keyforge.iiq.user.UserRepository;
import com.keyforge.iiq.usergroup.UserGroupRepository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves every repository resolves its target table from the configured schema:
 * the no-arg constructor defaults to {@code migration_test}, an explicit schema is
 * honoured, and no repository silently falls back to {@code migration_test} once a
 * custom schema is supplied.
 */
class RepositorySchemaTest {

    /** (table suffix, default-ctor targetTable, schema-ctor factory). */
    private record RepoCase(String table, String defaultTargetTable, Function<String, String> targetForSchema) {
    }

    private static final List<RepoCase> REPOS = List.of(
            new RepoCase("kf_identity",
                    new UserRepository().targetTable(),
                    s -> new UserRepository(s).targetTable()),
            new RepoCase("kf_application",
                    new ApplicationRepository().targetTable(),
                    s -> new ApplicationRepository(s).targetTable()),
            new RepoCase("applicationinstance",
                    new ApplicationInstanceRepository().targetTable(),
                    s -> new ApplicationInstanceRepository(s).targetTable()),
            new RepoCase("kf_account",
                    new AccountRepository().targetTable(),
                    s -> new AccountRepository(s).targetTable()),
            new RepoCase("kf_entitlement",
                    new EntitlementRepository().targetTable(),
                    s -> new EntitlementRepository(s).targetTable()),
            new RepoCase("entitlementassignment",
                    new EntitlementAssignmentRepository().targetTable(),
                    s -> new EntitlementAssignmentRepository(s).targetTable()),
            new RepoCase("catalog",
                    new CatalogRepository().targetTable(),
                    s -> new CatalogRepository(s).targetTable()),
            new RepoCase("usergroup",
                    new UserGroupRepository().targetTable(),
                    s -> new UserGroupRepository(s).targetTable()));

    @Test
    void defaultConstructorTargetsMigrationTestSchema() {
        for (RepoCase repo : REPOS) {
            assertEquals("migration_test." + repo.table(), repo.defaultTargetTable(),
                    "default schema for " + repo.table());
        }
    }

    @Test
    void customSchemaIsHonouredByEveryRepository() {
        String custom = "acme_prod";
        for (RepoCase repo : REPOS) {
            assertEquals(custom + "." + repo.table(), repo.targetForSchema().apply(custom),
                    "custom schema for " + repo.table());
        }
    }

    @Test
    void noRepositoryFallsBackToMigrationTestWhenCustomSchemaSupplied() {
        String custom = "my_custom_schema";
        for (RepoCase repo : REPOS) {
            String target = repo.targetForSchema().apply(custom);
            assertTrue(target.startsWith(custom + "."),
                    repo.table() + " should target the custom schema but was " + target);
            assertFalse(target.contains("migration_test"),
                    repo.table() + " must not fall back to migration_test but was " + target);
        }
    }

    @Test
    void coreEntitiesTargetCanonicalPdfNamesNotLegacyNames() {
        // The four core PDF entities must be produced under canonical names by the persistence path.
        assertEquals("migration_test.kf_identity", new UserRepository().targetTable());
        assertEquals("migration_test.kf_account", new AccountRepository().targetTable());
        assertEquals("migration_test.kf_application", new ApplicationRepository().targetTable());
        assertEquals("migration_test.kf_entitlement", new EntitlementRepository().targetTable());
        // No core repository may still target a legacy physical name.
        for (String legacy : List.of("migration_test.usr", "migration_test.account",
                "migration_test.application", "migration_test.entitlement")) {
            for (String target : List.of(new UserRepository().targetTable(), new AccountRepository().targetTable(),
                    new ApplicationRepository().targetTable(), new EntitlementRepository().targetTable())) {
                assertFalse(target.equals(legacy), "no core repo may target legacy " + legacy);
            }
        }
    }

    @Test
    void schemaAccessorMatchesConfiguredSchema() {
        assertEquals("migration_test", new UserRepository().schema());
        assertEquals("acme_prod", new UserRepository("acme_prod").schema());
        assertEquals("acme_prod", new CatalogRepository("acme_prod").schema());
        assertEquals("acme_prod", new EntitlementAssignmentRepository("acme_prod").schema());
        assertEquals("acme_prod", new UserGroupRepository("acme_prod").schema());
    }
}
