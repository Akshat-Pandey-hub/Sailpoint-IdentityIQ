package com.keyforge.nativeload;

import com.keyforge.iiq.parquet.ParquetIds;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Deterministic PK + record-hash behaviour for native GroupDefinition persistence. */
class NativeGroupDefinitionRepositoryTest {

    private static NativeGroupDefinitionRecord record(String sourceId, String name, String type) {
        NativeGroupDefinitionRecord r = new NativeGroupDefinitionRecord();
        r.sourceId = sourceId;
        r.name = name;
        r.type = type;
        return r;
    }

    @Test
    void groupIdIsCanonicalUuidOfSourceId() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String id = NativeGroupDefinitionRepository.canonicalGroupId(record(hex, "Dept-HR", "GROUP"));
        assertEquals(ParquetIds.canonicalUuid(hex), id);
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void unparseableIdFallsBackToStableDeterministicUuid() {
        String a = NativeGroupDefinitionRepository.canonicalGroupId(record(null, "Contractors", "POPULATION"));
        String b = NativeGroupDefinitionRepository.canonicalGroupId(record(null, "Contractors", "POPULATION"));
        assertNotNull(a);
        assertEquals(a, b, "fallback must be deterministic, never random");
        assertEquals(a, UUID.fromString(a).toString());
    }

    @Test
    void recordHashChangesWhenTypeChanges() {
        String hex = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        String asGroup = NativeGroupDefinitionRepository.recordHash(record(hex, "X", "GROUP"));
        String asGroup2 = NativeGroupDefinitionRepository.recordHash(record(hex, "X", "GROUP"));
        String asPopulation = NativeGroupDefinitionRepository.recordHash(record(hex, "X", "POPULATION"));
        assertNotNull(asGroup);
        assertEquals(asGroup, asGroup2);
        assertNotEquals(asGroup, asPopulation, "type is a business field and must change the hash");
    }
}
