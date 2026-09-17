package com.keyforge.iiq.catalog;

import com.keyforge.iiq.model.Entitlement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety invariant for the catalog deletion sweep: its keep-set is rebuilt with
 * {@code deriveCatalogRows(requestable, Set.of(), Set.of()).catalogid()}, while persistence derives
 * with the real existing-id sets. Since {@code catalogid = deterministicCatalogId(name, appinstanceid)}
 * is independent of those sets, both must yield the SAME catalogids — otherwise the sweep could mark a
 * live catalog row deleted. Also verifies duplicate (name, app) entitlements collapse to one catalog
 * id (matching persistence's grouping), so the keep-set never omits a stored row.
 */
class CatalogDeletionKeyTest {

    private static final String APP = "7f0001019eb91d3c819f01f0c4127efb";

    private static Entitlement ent(String id, String displayName, String value) {
        return new Entitlement(id, displayName, value, "posixgroups", Boolean.TRUE, "posixgroup",
                new Entitlement.ApplicationRef("LDAP-Target", APP, null));
    }

    private static Set<String> catalogIds(List<CatalogRow> rows) {
        return rows.stream().map(CatalogRow::catalogid).collect(Collectors.toSet());
    }

    @Test
    void keepSetIsIndependentOfExistingIdSets() {
        List<Entitlement> reqs = List.of(
                ent("ent1", "devs", "cn=devs,ou=x"),
                ent("ent3", "hr", "cn=hr,ou=x"));
        Set<String> emptySetDerivation = catalogIds(CatalogRowMapper.deriveCatalogRows(reqs, Set.of(), Set.of()));
        Set<String> realSetDerivation = catalogIds(CatalogRowMapper.deriveCatalogRows(
                reqs, Set.of("some-entitlement-id"), Set.of("some-instance-id")));
        assertEquals(realSetDerivation, emptySetDerivation,
                "sweep keep-set (empty sets) must equal the persisted catalogids (real sets)");
    }

    @Test
    void duplicateNameAndAppCollapseToOneCatalogId() {
        // two requestable entitlements sharing (name, application) -> one catalog row / id
        List<Entitlement> reqs = List.of(
                ent("ent1", "devs", "cn=devs,ou=x"),
                ent("ent2", "devs", "cn=devs2,ou=x"),
                ent("ent3", "hr", "cn=hr,ou=x"));
        Set<String> ids = catalogIds(CatalogRowMapper.deriveCatalogRows(reqs, Set.of(), Set.of()));
        assertEquals(2, ids.size(), "duplicate (name, app) must collapse like persistence");
    }

    @Test
    void catalogIdMatchesDeterministicFormula() {
        Entitlement e = ent("ent1", "devs", "cn=devs,ou=x");
        List<CatalogRow> rows = CatalogRowMapper.deriveCatalogRows(List.of(e), Set.of(), Set.of());
        String expected = CatalogRowMapper.deterministicCatalogId(
                CatalogRowMapper.catalogName(e), CatalogRowMapper.canonicalAppInstanceId(e));
        assertTrue(catalogIds(rows).contains(expected), "catalogid must equal deterministicCatalogId(name, appinstanceid)");
    }
}
