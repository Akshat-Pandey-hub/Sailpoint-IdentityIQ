package com.keyforge.iiq.entitlement;

import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.model.Entitlement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementServiceTest {

    /** Fake client that serves canned SCIM pages and records requested startIndex values. */
    private static final class FakeClient extends IiqApiClient {
        private final List<String> pages;
        final List<String> requestedStartIndexes = new ArrayList<>();

        FakeClient(List<String> pages) {
            super(); // test double: overrides get(), needs no live HTTP client
            this.pages = pages;
        }

        @Override
        public String get(String path, Map<String, String> queryParams) {
            requestedStartIndexes.add(queryParams.get("startIndex"));
            int served = requestedStartIndexes.size() - 1;
            if (served >= pages.size()) {
                return "{\"totalResults\":0,\"startIndex\":1,\"Resources\":[]}";
            }
            return pages.get(served);
        }
    }

    @Test
    void retrievesAllEntitlementsFromSinglePage() {
        String page = "{\"totalResults\":2,\"startIndex\":1,\"Resources\":["
                + ent("e1", "Admins", "group", "groups", "EntraTarget")
                + "," + ent("e2", "Users", "group", "groups", "EntraTarget")
                + "]}";

        FakeClient client = new FakeClient(List.of(page));
        List<Entitlement> ents = new EntitlementService(client).getAllEntitlements();

        assertEquals(2, ents.size());
        assertEquals(List.of("e1", "e2"), ents.stream().map(Entitlement::getId).toList());
        assertEquals(1, client.requestedStartIndexes.size()); // single page fetched
    }

    @Test
    void retrievesAllEntitlementsAcrossMultiplePages() {
        // totalResults = 3, first page of 2, second page of 1.
        String page1 = "{\"totalResults\":3,\"startIndex\":1,\"itemsPerPage\":2,\"Resources\":["
                + ent("e1", "Admins", "group", "groups", "EntraTarget")
                + "," + ent("e2", "Ops", "posixgroup", "posixgroups", "corp directory-LDAP-Target")
                + "]}";
        String page2 = "{\"totalResults\":3,\"startIndex\":3,\"itemsPerPage\":2,\"Resources\":["
                + ent("e3", "Auditors", "group", "groups", "EntraTarget")
                + "]}";

        FakeClient client = new FakeClient(List.of(page1, page2));
        List<Entitlement> ents = new EntitlementService(client).getAllEntitlements();

        assertEquals(3, ents.size());
        assertEquals(List.of("e1", "e2", "e3"), ents.stream().map(Entitlement::getId).toList());
        // startIndex advances by the number of resources actually returned.
        assertEquals(List.of("1", "3"), client.requestedStartIndexes);
    }

    @Test
    void handlesEmptyResources() {
        // Server claims a total but returns nothing: must not loop forever.
        String onlyPage = "{\"totalResults\":37,\"startIndex\":1,\"Resources\":[]}";
        FakeClient client = new FakeClient(List.of(onlyPage));

        List<Entitlement> ents = new EntitlementService(client).getAllEntitlements();

        assertTrue(ents.isEmpty());
        assertEquals(1, client.requestedStartIndexes.size());
    }

    @Test
    void mapsObservedEntitlementFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"ent-77\","
                + "\"displayableName\":\"Domain Admins\","
                + "\"value\":\"CN=Domain Admins\","
                + "\"attribute\":\"groups\","
                + "\"requestable\":true,"
                + "\"type\":\"group\""
                + "}]}";

        FakeClient client = new FakeClient(List.of(page));
        Entitlement ent = new EntitlementService(client).getAllEntitlements().get(0);

        assertEquals("ent-77", ent.getId());
        assertEquals("Domain Admins", ent.getDisplayableName());
        assertEquals("CN=Domain Admins", ent.getValue());
        assertEquals("groups", ent.getAttribute());
        assertEquals(Boolean.TRUE, ent.getRequestable());
        assertEquals("group", ent.getType());
    }

    @Test
    void mapsNestedApplicationReference() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"ent-9\",\"displayableName\":\"posix ops\",\"value\":\"ops\","
                + "\"attribute\":\"posixgroups\",\"type\":\"posixgroup\","
                + "\"application\":{"
                + "\"displayName\":\"corp directory-LDAP-Target\","
                + "\"value\":\"app-123\","
                + "\"$ref\":\"https://iiq/scim/v2/Applications/app-123\"}"
                + "}]}";

        FakeClient client = new FakeClient(List.of(page));
        Entitlement ent = new EntitlementService(client).getAllEntitlements().get(0);

        assertNotNull(ent.getApplication());
        assertEquals("corp directory-LDAP-Target", ent.getApplication().getDisplayName());
        assertEquals("app-123", ent.getApplication().getValue());
        assertEquals("https://iiq/scim/v2/Applications/app-123", ent.getApplication().getRef());
    }

    @Test
    void toleratesMissingOptionalFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"ent-min\",\"value\":\"raw-value\"}]}";
        FakeClient client = new FakeClient(List.of(page));

        Entitlement ent = new EntitlementService(client).getAllEntitlements().get(0);

        assertEquals("ent-min", ent.getId());
        assertEquals("raw-value", ent.getValue());
        assertNull(ent.getDisplayableName());
        assertNull(ent.getAttribute());
        assertNull(ent.getRequestable());
        assertNull(ent.getType());
        assertNull(ent.getApplication());
    }

    @Test
    void additionalAttributesPreserveRawSourceExceptCapturedColumns() {
        // Includes an unmodelled field ("owner") to prove nothing is dropped.
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"ent-9\",\"value\":\"ops\",\"displayableName\":\"posix ops\","
                + "\"attribute\":\"posixgroups\",\"type\":\"posixgroup\",\"requestable\":true,"
                + "\"owner\":{\"displayName\":\"Molly J\"},"
                + "\"meta\":{\"resourceType\":\"Entitlement\"}}]}";

        FakeClient client = new FakeClient(List.of(page));
        Entitlement ent = new EntitlementService(client).getAllEntitlements().get(0);

        // Raw type and any other source field are preserved verbatim...
        assertEquals("posixgroup", ent.getAdditionalAttributes().path("type").asText());
        assertEquals("posixgroups", ent.getAdditionalAttributes().path("attribute").asText());
        assertEquals("Molly J", ent.getAdditionalAttributes().path("owner").path("displayName").asText());
        assertEquals("Entitlement", ent.getAdditionalAttributes().path("meta").path("resourceType").asText());
        // ...except the fields fully captured in dedicated columns.
        assertFalse(ent.getAdditionalAttributes().has("id"));
        assertFalse(ent.getAdditionalAttributes().has("value"));
        assertFalse(ent.getAdditionalAttributes().has("displayableName"));
    }

    private static String ent(String id, String displayableName, String type, String attribute, String appName) {
        return "{\"id\":\"" + id + "\",\"displayableName\":\"" + displayableName + "\","
                + "\"value\":\"" + displayableName + "\",\"attribute\":\"" + attribute + "\","
                + "\"requestable\":true,\"type\":\"" + type + "\","
                + "\"application\":{\"displayName\":\"" + appName + "\",\"value\":\"app-" + id + "\","
                + "\"$ref\":\"https://iiq/scim/v2/Applications/app-" + id + "\"}}";
    }
}
