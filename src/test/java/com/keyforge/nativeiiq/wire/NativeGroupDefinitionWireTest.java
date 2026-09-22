package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeGroupDefinitionExtractionResult;
import com.keyforge.nativeiiq.model.NativeGroupDefinitionRow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the GroupDefinition wire envelope preserves the GROUP/POPULATION type and is a JSON object. */
class NativeGroupDefinitionWireTest {

    private static final String[] REQUIRED_KEYS = {
            "sourceId", "name", "type", "factoryId", "factoryName", "filterExpression", "isPrivate",
            "indexed", "nullGroup", "nameUnique", "ownerId", "ownerName", "lastRefresh", "created",
            "modified", "srcSystem", "srcInterface", "srcObjectType", "extractionRunId", "extractedAt"
    };

    private static NativeGroupDefinitionRow groupRow() {
        NativeGroupDefinitionRow r = new NativeGroupDefinitionRow();
        r.setSourceId("g1");
        r.setName("Dept-HR");
        r.setType("GROUP");
        r.setFactoryId("fac-1");
        r.setFactoryName("Department");
        r.setIndexed(Boolean.TRUE);
        r.setIsPrivate(Boolean.FALSE);
        r.setCreated(Instant.EPOCH);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        return r;
    }

    private static NativeGroupDefinitionRow populationRow() {
        NativeGroupDefinitionRow r = new NativeGroupDefinitionRow();
        r.setSourceId("p1");
        r.setName("Contractors");
        r.setType("POPULATION");
        r.setFilterExpression("type == \"contractor\"");
        r.setIsPrivate(Boolean.TRUE);
        r.setCreated(Instant.EPOCH);
        r.setSrcSystem("IdentityIQ");
        r.setExtractionRunId("run-1");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void envelopePreservesGroupAndPopulationTypes() {
        NativeGroupDefinitionExtractionResult result =
                new NativeGroupDefinitionExtractionResult("IdentityIQ", "run-1", "GroupDefinition", Instant.EPOCH);
        result.getGroupDefinitions().add(groupRow());
        result.getGroupDefinitions().add(populationRow());

        Map<String, Object> env = NativeGroupDefinitionWire.envelope(result, 0, 3);
        assertEquals("GroupDefinition", env.get("entity"));
        assertEquals(Integer.valueOf(2), env.get("returned"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) env.get("rows");
        for (String key : REQUIRED_KEYS) {
            assertTrue(rows.get(0).containsKey(key), "row must contain field: " + key);
        }
        assertEquals("GROUP", rows.get(0).get("type"));
        assertEquals("Department", rows.get(0).get("factoryName"));
        assertEquals("POPULATION", rows.get(1).get("type"));
        assertEquals("type == \"contractor\"", rows.get(1).get("filterExpression"));
        assertEquals("sailpoint.object.GroupDefinition", rows.get(0).get("srcObjectType"));
    }

    @Test
    void envelopeIsAJsonObjectNotADoubleEncodedString() throws Exception {
        NativeGroupDefinitionExtractionResult result =
                new NativeGroupDefinitionExtractionResult("IdentityIQ", "run-1", "GroupDefinition", Instant.EPOCH);
        result.getGroupDefinitions().add(groupRow());
        Map<String, Object> env = NativeGroupDefinitionWire.envelope(result, 0, 3);
        ObjectMapper mapper = new ObjectMapper();

        String once = mapper.writeValueAsString(env);
        assertTrue(once.startsWith("{"), "response must be a JSON object, not a quoted string");
        assertTrue(once.contains("\"entity\":\"GroupDefinition\""));

        String doubleEncoded = mapper.writeValueAsString(once);
        assertTrue(doubleEncoded.startsWith("\"{"), "handing the provider a JSON String re-encodes it (the bug)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorEnvelopeCapturesThrowable() {
        Map<String, Object> env = NativeGroupDefinitionWire.errorEnvelope(500, new NoClassDefFoundError("X"));
        assertEquals("GroupDefinition", env.get("entity"));
        assertEquals(Integer.valueOf(500), env.get("status"));
        assertEquals("java.lang.NoClassDefFoundError", ((Map<String, Object>) env.get("error")).get("type"));
    }
}
