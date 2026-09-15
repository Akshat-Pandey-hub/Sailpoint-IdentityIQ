package com.keyforge.iiq.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Application;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Application → {@code application} migration mapping: Application fields
 * are stored on THIS table (not pushed to applicationinstance), schemas/features/
 * descriptions are preserved as JSON arrays, and absent fields are NULL.
 */
class ApplicationRowMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String APP_ID_HEX = "7f00010198421229819849c815b90bfc";
    private static final String APP_ID_CANON = "7f000101-9842-1229-8198-49c815b90bfc";
    private static final String OWNER_HEX = "7f000101971416688197147684ad00ff";
    private static final String OWNER_CANON = "7f000101-9714-1668-8197-147684ad00ff";

    @Test
    void mapsApplicationFieldsToTheApplicationTable() {
        Application app = new Application(APP_ID_HEX, "EntraAuth", "Azure Active Directory",
                new Application.Owner(OWNER_HEX, "https://iiq/Users/" + OWNER_HEX, "Molly J"),
                List.of(new Application.ApplicationSchema("account", "sch-1", "https://iiq/Schemas/sch-1")),
                new Application.Meta("Application", "https://iiq/x", "2025-07-27T02:48:26.298Z",
                        "2025-07-27T04:13:13.619Z", "W/\"1\""));

        ApplicationRow row = ApplicationRowMapper.map(app);

        assertEquals(APP_ID_CANON, row.applicationid());
        assertEquals("EntraAuth", row.name());
        assertEquals("Azure Active Directory", row.type());
        assertEquals(OWNER_CANON, row.ownerId());
        assertEquals("Molly J", row.ownerDisplayName());
        assertEquals(LocalDateTime.of(2025, 7, 27, 2, 48, 26, 298_000_000), row.createdAt());
    }

    @Test
    void preservesSchemasFeaturesAndDescriptionsAsArrays() throws Exception {
        ObjectNode extra = MAPPER.createObjectNode();
        extra.putArray("features").add("PROVISIONING").add("AUTHENTICATE");
        extra.putArray("descriptions").addObject().put("locale", "en_US").put("value", "an app");

        Application app = new Application(APP_ID_HEX, "EntraAuth", "Azure Active Directory", null,
                List.of(new Application.ApplicationSchema("account", "sch-1", "https://iiq/Schemas/sch-1"),
                        new Application.ApplicationSchema("group", "sch-2", "https://iiq/Schemas/sch-2")),
                null, extra);

        ApplicationRow row = ApplicationRowMapper.map(app);

        JsonNode schemas = MAPPER.readTree(row.schemasJson());
        assertTrue(schemas.isArray());
        assertEquals(2, schemas.size());
        assertEquals("account", schemas.get(0).path("type").asText());
        assertEquals("sch-2", schemas.get(1).path("value").asText());

        JsonNode features = MAPPER.readTree(row.featuresJson());
        assertEquals(2, features.size());
        assertEquals("PROVISIONING", features.get(0).asText());

        JsonNode descriptions = MAPPER.readTree(row.descriptionsJson());
        assertEquals("en_US", descriptions.get(0).path("locale").asText());
    }

    @Test
    void featuresRealStringsArePreservedAsAJsonbArray() throws Exception {
        ObjectNode extra = MAPPER.createObjectNode();
        extra.putArray("features").add("PROVISIONING").add("AUTHENTICATE").add("ENABLE");
        Application app = new Application(APP_ID_HEX, "EntraAuth", "Azure Active Directory", null,
                List.of(), null, extra);

        JsonNode features = MAPPER.readTree(ApplicationRowMapper.map(app).featuresJson());
        assertTrue(features.isArray());
        assertEquals(3, features.size());              // ALL values kept
        assertEquals("PROVISIONING", features.get(0).asText());
        assertEquals("ENABLE", features.get(2).asText());
    }

    @Test
    void featuresJavaArrayArtifactIsExcludedNotStored() throws Exception {
        // IIQ's serialization artifact must NOT be stored as a value.
        ObjectNode onlyArtifact = MAPPER.createObjectNode();
        onlyArtifact.putArray("features").add("[Ljava.lang.String;@626cb311");
        Application app = new Application(APP_ID_HEX, "X", "JDBC", null, List.of(), null, onlyArtifact);
        assertNull(ApplicationRowMapper.map(app).featuresJson());     // -> NULL, no garbage

        // Mixed: real values kept, artifact dropped.
        ObjectNode mixed = MAPPER.createObjectNode();
        mixed.putArray("features").add("PROVISIONING").add("[Ljava.lang.String;@abc123");
        JsonNode features = MAPPER.readTree(
                ApplicationRowMapper.map(new Application(APP_ID_HEX, "X", "JDBC", null, List.of(), null, mixed))
                        .featuresJson());
        assertEquals(1, features.size());
        assertEquals("PROVISIONING", features.get(0).asText());

        // The detector matches Java array toString but not normal feature names.
        assertTrue(ApplicationRowMapper.isJavaArrayArtifact("[Ljava.lang.String;@626cb311"));
        assertTrue(ApplicationRowMapper.isJavaArrayArtifact("[I@1f2e3d"));
        assertTrue(!ApplicationRowMapper.isJavaArrayArtifact("PROVISIONING"));
        assertTrue(!ApplicationRowMapper.isJavaArrayArtifact("ENABLE_PASSWORD_POLICY"));
    }

    @Test
    void descriptionsArePreservedExactlyAsIiqReturnsThem() throws Exception {
        // IIQ returns description entries; here only 'locale' is present -> keep as-is.
        ObjectNode extra = MAPPER.createObjectNode();
        extra.putArray("descriptions").addObject().put("locale", "en_US");
        Application app = new Application(APP_ID_HEX, "X", "JDBC", null, List.of(), null, extra);

        JsonNode descriptions = MAPPER.readTree(ApplicationRowMapper.map(app).descriptionsJson());
        assertEquals(1, descriptions.size());
        assertEquals("en_US", descriptions.get(0).path("locale").asText());
    }

    @Test
    void absentFieldsAreNull() {
        Application app = new Application(APP_ID_HEX, null, null, null, List.of(), null);
        ApplicationRow row = ApplicationRowMapper.map(app);
        assertEquals(APP_ID_CANON, row.applicationid());
        assertNull(row.name());
        assertNull(row.type());
        assertNull(row.ownerId());
        assertNull(row.ownerDisplayName());
        assertNull(row.schemasJson());
        assertNull(row.featuresJson());
        assertNull(row.descriptionsJson());
        assertNull(row.createdAt());
        assertNull(row.modifiedAt());
    }

    @Test
    void invalidIdIsRejected() {
        Application app = new Application("bad-id", "X", "JDBC", null, List.of(), null);
        assertThrows(ApplicationMappingException.class, () -> ApplicationRowMapper.map(app));
    }
}
