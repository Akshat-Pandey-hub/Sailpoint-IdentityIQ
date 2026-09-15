package com.keyforge.iiq.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the configurable target schema ({@code PG_SCHEMA}): it defaults to
 * {@code migration_test}, honours an explicit value, and rejects anything that is not
 * a safe SQL identifier.
 */
class PgConfigTest {

    private static PgConfig config(String schema) {
        return new PgConfig("localhost", "5432", "my_local_db", "postgres", "secret", schema);
    }

    @Test
    void defaultsToMigrationTestWhenSchemaUnset() {
        // The 5-arg constructor mirrors "PG_SCHEMA not set".
        PgConfig noSchemaArg = new PgConfig("localhost", "5432", "db", "u", "p");
        assertEquals("migration_test", noSchemaArg.getSchema());
        assertEquals("migration_test", config(null).getSchema());
        assertEquals("migration_test", config("").getSchema());
        assertEquals("migration_test", config("   ").getSchema());
    }

    @Test
    void honoursExplicitSchema() {
        assertEquals("my_custom_schema", config("my_custom_schema").getSchema());
        assertEquals("acme_prod", config("acme_prod").getSchema());
    }

    @Test
    void trimsSchema() {
        assertEquals("prod_schema", config("  prod_schema  ").getSchema());
    }

    @Test
    void rejectsInvalidSchemaIdentifiers() {
        assertThrows(ConfigException.class, () -> config("has-a-dash"));
        assertThrows(ConfigException.class, () -> config("1startsWithDigit"));
        assertThrows(ConfigException.class, () -> config("has space"));
        assertThrows(ConfigException.class, () -> config("drop;table"));
        assertThrows(ConfigException.class, () -> config("quote\"inside"));
    }

    @Test
    void schemaDoesNotAffectJdbcUrl() {
        assertEquals("jdbc:postgresql://localhost:5432/my_local_db", config("acme_prod").getJdbcUrl());
    }
}
