package com.keyforge.iiq.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.model.Account;
import com.keyforge.iiq.model.AccountEntitlementAssignment;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionSource;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionStatus;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Identity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountEntitlementAssignmentServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AccountEntitlementAssignmentService service = new AccountEntitlementAssignmentService();

    // --- builders -----------------------------------------------------------

    private static Entitlement entitlement(String id, String value, String attribute, String type,
                                           String appId, String appName) {
        return new Entitlement(id, value, value, attribute, true, type,
                new Entitlement.ApplicationRef(appName, appId, "https://iiq/scim/v2/Applications/" + appId));
    }

    /** Builds an Account whose additionalAttributes are parsed from the given JSON body. */
    private static Account account(String id, String nativeIdentity, String appId, String appName,
                                   String identityId, String identityName, String additionalJson) {
        try {
            var extra = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(additionalJson);
            return new Account(id, nativeIdentity, nativeIdentity, true, false, false, true, null,
                    new Account.Ref(appName, null, appId, "https://iiq/scim/v2/Applications/" + appId),
                    new Account.Ref(identityName, identityName, identityId, "https://iiq/scim/v2/Users/" + identityId),
                    null, List.of(), extra);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Identity identityWithEntitlements(String id, String displayName, String entitlementsJson) {
        try {
            JsonNode arr = MAPPER.readTree(entitlementsJson);
            List<JsonNode> refs = new ArrayList<>();
            arr.forEach(refs::add);
            return new Identity(id, displayName, displayName, true, null, null, null, List.of(), refs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- tests --------------------------------------------------------------

    @Test
    void explodesArrayIntoIndividualAssignments() {
        // Account "groups" = [A, B, C] must yield three separate assignments.
        List<Entitlement> cat = List.of(
                entitlement("e-A", "A", "groups", "group", "app1", "EntraAuth"),
                entitlement("e-B", "B", "groups", "group", "app1", "EntraAuth"),
                entitlement("e-C", "C", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"urn:app:schema\":{\"groups\":[\"A\",\"B\",\"C\"]}}");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat);

        assertEquals(3, result.size());
        assertEquals(List.of("e-A", "e-B", "e-C"),
                result.stream().map(AccountEntitlementAssignment::getEntitlementId).toList());
        assertTrue(result.stream().allMatch(a -> a.getResolutionStatus() == ResolutionStatus.RESOLVED));
        assertTrue(result.stream().allMatch(a -> "groups".equals(a.getSourceAttribute())));
    }

    @Test
    void handlesDifferentAttributeNameGenericallyWithoutCodeChange() {
        // A different application uses "roles" — no special-casing anywhere.
        List<Entitlement> cat = List.of(
                entitlement("r-X", "X", "roles", "role", "app2", "SomeApp"),
                entitlement("r-Y", "Y", "roles", "role", "app2", "SomeApp"));
        Account acc = account("acc9", "bob", "app2", "SomeApp", "id9", "Bob",
                "{\"roles\":[\"X\",\"Y\"]}");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat);

        assertEquals(2, result.size());
        assertEquals(List.of("r-X", "r-Y"),
                result.stream().map(AccountEntitlementAssignment::getEntitlementId).toList());
    }

    @Test
    void resolvesNestedApplicationSchemaWrapper() {
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        // groups is nested inside an application-schema object, like the real payload.
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"urn:ietf:params:scim:schemas:Application:Schema:EntraAuth:account\":{\"groups\":[\"G1\"]}}");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat);

        assertEquals(1, result.size());
        assertEquals("e1", result.get(0).getEntitlementId());
        assertEquals("G1", result.get(0).getEntitlementValue());
    }

    @Test
    void ignoresNonEntitlementAttributes() {
        // Catalogue only knows "groups"; proxyAddresses/mail must NOT become assignments.
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G1\"],\"proxyAddresses\":[\"smtp:a@x.com\",\"smtp:b@x.com\"],\"mail\":\"a@x.com\"}");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat);

        assertEquals(1, result.size());
        assertEquals("groups", result.get(0).getSourceAttribute());
        assertEquals("e1", result.get(0).getEntitlementId());
    }

    @Test
    void keepsUnresolvedValuesInsteadOfDropping() {
        // "G2" is under an entitlement attribute but absent from the catalogue.
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G1\",\"G2\"]}");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat);

        assertEquals(2, result.size());
        AccountEntitlementAssignment unresolved = result.stream()
                .filter(a -> "G2".equals(a.getEntitlementValue())).findFirst().orElseThrow();
        assertEquals(ResolutionStatus.UNRESOLVED, unresolved.getResolutionStatus());
        assertNull(unresolved.getEntitlementId());
        // The value and attribute are preserved for later investigation.
        assertEquals("groups", unresolved.getSourceAttribute());
    }

    @Test
    void preservesAccountToIdentityAndApplicationReferences() {
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id-42", "Alice Smith",
                "{\"groups\":[\"G1\"]}");

        AccountEntitlementAssignment a = service.build(List.of(acc), cat).get(0);

        assertEquals("acc1", a.getAccountId());
        assertEquals("id-42", a.getIdentityId());
        assertEquals("app1", a.getApplicationId());
        assertEquals("EntraAuth", a.getApplicationName());
    }

    @Test
    void deduplicatesRepeatedValues() {
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        // Same value listed twice on the same account.
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G1\",\"G1\"]}");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat);

        assertEquals(1, result.size());
    }

    @Test
    void userExtensionValidatesAccountDerivedAssignment() {
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G1\"]}");
        Identity id = identityWithEntitlements("id1", "Alice",
                "[{\"application\":\"EntraAuth\",\"accountName\":\"alice\",\"display\":\"G1\","
                + "\"type\":\"Entitlement\",\"value\":\"groups\","
                + "\"$ref\":\"https://iiq/scim/v2/Entitlements/e1\"}]");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat, List.of(id));

        // Same logical assignment from both passes -> a single, validated row.
        assertEquals(1, result.size());
        AccountEntitlementAssignment a = result.get(0);
        assertEquals("e1", a.getEntitlementId());
        assertTrue(a.getResolutionSources().contains(ResolutionSource.ACCOUNT_ATTRIBUTE));
        assertTrue(a.getResolutionSources().contains(ResolutionSource.USER_EXTENSION));
    }

    @Test
    void userExtensionResolvesWhatCatalogueValueMatchMissed() {
        // Catalogue does NOT contain the native value, so the account pass is unresolved;
        // the user-extension $ref supplies the entitlement id (fallback).
        List<Entitlement> cat = List.of(
                entitlement("e-known", "OTHER", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G-NATIVE\"]}");
        Identity id = identityWithEntitlements("id1", "Alice",
                "[{\"application\":\"EntraAuth\",\"accountName\":\"alice\",\"display\":\"G-NATIVE\","
                + "\"type\":\"Entitlement\",\"value\":\"groups\","
                + "\"$ref\":\"https://iiq/scim/v2/Entitlements/e-native\"}]");

        List<AccountEntitlementAssignment> result = service.build(List.of(acc), cat, List.of(id));

        AccountEntitlementAssignment a = result.stream()
                .filter(x -> "G-NATIVE".equals(x.getEntitlementValue())).findFirst().orElseThrow();
        assertEquals("e-native", a.getEntitlementId());
        assertEquals(ResolutionStatus.RESOLVED, a.getResolutionStatus());
        assertTrue(a.getResolutionSources().contains(ResolutionSource.USER_EXTENSION));
    }

    @Test
    void unavailableTargetFieldsAreNullNotFabricated() {
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G1\"]}");

        AccountEntitlementAssignment a = service.build(List.of(acc), cat).get(0);

        assertNull(a.getProvisioningMechanism());
        assertNull(a.getAssignedDate());
        assertNull(a.getExpirationDate());
        assertNull(a.getStatus());
        assertNull(a.getAssignedBy());
        assertNull(a.getAssignmentId());
    }

    @Test
    void customAttributesPreserveContext() {
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app1", "EntraAuth"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G1\"]}");

        AccountEntitlementAssignment a = service.build(List.of(acc), cat).get(0);

        assertNotNull(a.getCustomAttributes());
        assertEquals("groups", a.getCustomAttributes().path("sourceAttribute").asText());
        assertEquals("G1", a.getCustomAttributes().path("nativeValue").asText());
        assertEquals("app1", a.getCustomAttributes().path("applicationId").asText());
    }

    @Test
    void emptyInputsProduceNoAssignments() {
        assertTrue(service.build(List.of(), List.of()).isEmpty());
        assertTrue(service.build(null, null, null).isEmpty());
    }

    @Test
    void applicationWithNoCatalogueEntitlementsProducesNothing() {
        // No entitlements for the account's application -> cannot identify entitlement
        // attributes without hardcoding, so nothing is emitted (no guessing).
        List<Entitlement> cat = List.of(
                entitlement("e1", "G1", "groups", "group", "app-other", "OtherApp"));
        Account acc = account("acc1", "alice", "app1", "EntraAuth", "id1", "Alice",
                "{\"groups\":[\"G1\"]}");

        assertTrue(service.build(List.of(acc), cat).isEmpty());
    }
}
