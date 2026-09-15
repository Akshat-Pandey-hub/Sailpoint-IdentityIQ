package com.keyforge.iiq.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Entitlement;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the derived requestable-entitlement catalog: identity (name, appinstanceid),
 * primary selection (aggregated=false), deterministic id, FK resolution, and compact
 * derivation provenance in {@code source_entitlements} (no ISPM columns/defaults).
 */
class CatalogRowMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ENT_ID_HEX = "7f00010198421229819849f9859c0e4a";
    private static final String ENT_ID_CANON = "7f000101-9842-1229-8198-49f9859c0e4a";
    private static final String APP_ID_HEX = "7f00010198421229819849f1427b0d58";
    private static final String APP_ID_CANON = "7f000101-9842-1229-8198-49f1427b0d58";

    private static Entitlement entitlement(String id, String displayableName, String value, String type,
                                           Boolean requestable, String appDisplayName, String appValue,
                                           String created, ObjectNode additional) {
        Entitlement.ApplicationRef app = appValue == null ? null
                : new Entitlement.ApplicationRef(appDisplayName, appValue, "https://iiq/Applications/" + appValue);
        Entitlement.Meta meta = created == null ? null
                : new Entitlement.Meta("Entitlement", "https://iiq/x", created, null, "W/\"1\"");
        ObjectNode extra = additional == null ? MAPPER.createObjectNode() : additional.deepCopy();
        return new Entitlement(id, displayableName, value, "groups", requestable, type, app, meta, extra);
    }

    private static CatalogRow deriveSingle(Entitlement e, Set<String> entIds, Set<String> instIds) {
        List<CatalogRow> rows = CatalogRowMapper.deriveCatalogRows(List.of(e), entIds, instIds);
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    @Test
    void mapsEntitlementToCatalogRow() {
        Entitlement e = entitlement(ENT_ID_HEX, "Domain Admins", "cn=admins", "group", true,
                "EntraTarget", APP_ID_HEX, "2024-01-02T03:04:05Z", null);

        CatalogRow row = deriveSingle(e, Set.of(ENT_ID_CANON), Set.of(APP_ID_CANON));

        assertEquals("Entitlement", row.type());
        assertEquals("Domain Admins", row.name());
        assertEquals(ENT_ID_CANON, row.entitlementid());
        assertEquals("cn=admins", row.entitlementName());
        assertEquals("group", row.entitlementType());
        assertEquals(Boolean.TRUE, row.requestable());
        assertEquals("EntraTarget", row.applicationName());
        assertEquals(APP_ID_CANON, row.appinstanceid());
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), row.createdAt());
    }

    @Test
    void catalogIdIsDeterministicPerIdentity() {
        assertEquals(CatalogRowMapper.deterministicCatalogId("devs", APP_ID_CANON),
                CatalogRowMapper.deterministicCatalogId("devs", APP_ID_CANON));
        assertNotEquals(CatalogRowMapper.deterministicCatalogId("devs", APP_ID_CANON),
                CatalogRowMapper.deterministicCatalogId("ops", APP_ID_CANON));
        assertNotEquals(CatalogRowMapper.deterministicCatalogId("devs", APP_ID_CANON),
                CatalogRowMapper.deterministicCatalogId("devs", "7f000101-9eb9-1d3c-819f-01f0c4127efb"));
    }

    @Test
    void duplicateEntitlementsCollapseAndNonAggregatedIsPrimary() {
        Entitlement aggregated = entitlement("7f0001019eb91d3c819f020fe42b7f45", "devs",
                "cn=devs,ou=rocktar,dc=moli,dc=org", "posixgroup", true, "corp directory-LDAP-Target",
                "7f0001019eb91d3c819f01f0c4127efb", "2026-06-26T03:53:43.723Z", null);
        aggregated.getAdditionalAttributes().put("aggregated", true);

        Entitlement nativeEntitlement = entitlement("7f0001019fbf1a17819fcd4fd4e01a25", "devs", "devs",
                "posixgroup", true, "corp directory-LDAP-Target",
                "7f0001019eb91d3c819f01f0c4127efb", "2026-08-04T15:06:28.960Z", null);
        nativeEntitlement.getAdditionalAttributes().put("aggregated", false);

        List<CatalogRow> rows = CatalogRowMapper.deriveCatalogRows(
                List.of(aggregated, nativeEntitlement),
                Set.of("7f000101-9eb9-1d3c-819f-020fe42b7f45", "7f000101-9fbf-1a17-819f-cd4fd4e01a25"),
                Set.of("7f000101-9eb9-1d3c-819f-01f0c4127efb"));

        assertEquals(1, rows.size());
        CatalogRow row = rows.get(0);
        assertEquals("devs", row.name());
        assertEquals("7f000101-9fbf-1a17-819f-cd4fd4e01a25", row.entitlementid()); // native (aggregated=false)
        assertEquals("devs", row.entitlementName());
    }

    @Test
    void primarySelectionIsStableRegardlessOfInputOrder() {
        Entitlement aggregated = entitlement("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "devs", "agg", "posixgroup",
                true, "corp directory-LDAP-Target", APP_ID_HEX, null, null);
        aggregated.getAdditionalAttributes().put("aggregated", true);
        Entitlement nonAggregated = entitlement("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "devs", "native", "posixgroup",
                true, "corp directory-LDAP-Target", APP_ID_HEX, null, null);
        nonAggregated.getAdditionalAttributes().put("aggregated", false);

        Set<String> ents = Set.of("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        List<CatalogRow> first = CatalogRowMapper.deriveCatalogRows(List.of(aggregated, nonAggregated), ents, Set.of(APP_ID_CANON));
        List<CatalogRow> second = CatalogRowMapper.deriveCatalogRows(List.of(nonAggregated, aggregated), ents, Set.of(APP_ID_CANON));

        assertEquals(first.get(0).catalogid(), second.get(0).catalogid());
        assertEquals(first.get(0).entitlementid(), second.get(0).entitlementid());
        assertEquals("native", first.get(0).entitlementName());
    }

    @Test
    void sourceEntitlementsDocumentEveryContributorCompactly() throws Exception {
        Entitlement aggregated = entitlement("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "devs", "agg", "posixgroup",
                true, "corp directory-LDAP-Target", APP_ID_HEX, null, null);
        aggregated.getAdditionalAttributes().put("aggregated", true);
        Entitlement nonAggregated = entitlement("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "devs", "native", "posixgroup",
                true, "corp directory-LDAP-Target", APP_ID_HEX, null, null);
        nonAggregated.getAdditionalAttributes().put("aggregated", false);

        List<CatalogRow> rows = CatalogRowMapper.deriveCatalogRows(List.of(aggregated, nonAggregated),
                Set.of("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                Set.of(APP_ID_CANON));

        JsonNode sources = MAPPER.readTree(rows.get(0).sourceEntitlementsJson()).path("sourceEntitlements");
        assertTrue(sources.isArray());
        assertEquals(2, sources.size());
        // PRIMARY first = the non-aggregated one, with compact provenance (value/type).
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", sources.get(0).path("entitlementId").asText());
        assertEquals("PRIMARY", sources.get(0).path("role").asText());
        assertEquals("native", sources.get(0).path("value").asText());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", sources.get(1).path("entitlementId").asText());
        assertEquals("ADDITIONAL", sources.get(1).path("role").asText());
    }

    @Test
    void unresolvedEntitlementAndInstanceLeftNull() {
        Entitlement e = entitlement(ENT_ID_HEX, "x", "x", "group", true, "App", APP_ID_HEX, null, null);
        CatalogRow row = deriveSingle(e, Set.of(), Set.of()); // neither resolves
        assertNull(row.entitlementid());
        assertNull(row.appinstanceid());
    }
}
