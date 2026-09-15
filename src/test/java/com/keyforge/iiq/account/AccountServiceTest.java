package com.keyforge.iiq.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.model.Account;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {

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

    private static List<Account> extract(String... pages) {
        return new AccountService(new FakeClient(List.of(pages))).getAllAccounts();
    }

    @Test
    void retrievesAllAccountsFromSinglePage() {
        String page = "{\"totalResults\":2,\"startIndex\":1,\"Resources\":["
                + account("a1", "cn=alice") + "," + account("a2", "cn=bob") + "]}";

        FakeClient client = new FakeClient(List.of(page));
        List<Account> accounts = new AccountService(client).getAllAccounts();

        assertEquals(2, accounts.size());
        assertEquals(List.of("a1", "a2"), accounts.stream().map(Account::getId).toList());
        assertEquals(1, client.requestedStartIndexes.size());
    }

    @Test
    void retrievesAllAccountsAcrossMultiplePages() {
        String page1 = "{\"totalResults\":3,\"startIndex\":1,\"itemsPerPage\":2,\"Resources\":["
                + account("a1", "cn=alice") + "," + account("a2", "cn=bob") + "]}";
        String page2 = "{\"totalResults\":3,\"startIndex\":3,\"itemsPerPage\":2,\"Resources\":["
                + account("a3", "cn=carol") + "]}";

        FakeClient client = new FakeClient(List.of(page1, page2));
        List<Account> accounts = new AccountService(client).getAllAccounts();

        assertEquals(3, accounts.size());
        assertEquals(List.of("a1", "a2", "a3"), accounts.stream().map(Account::getId).toList());
        assertEquals(List.of("1", "3"), client.requestedStartIndexes);
    }

    @Test
    void handlesEmptyResources() {
        String onlyPage = "{\"totalResults\":94,\"startIndex\":1,\"Resources\":[]}";
        FakeClient client = new FakeClient(List.of(onlyPage));

        List<Account> accounts = new AccountService(client).getAllAccounts();

        assertTrue(accounts.isEmpty());
        assertEquals(1, client.requestedStartIndexes.size());
    }

    @Test
    void mapsCoreAccountFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"acc-1\","
                + "\"displayName\":\"Alice Smith\","
                + "\"nativeIdentity\":\"cn=alice,dc=corp\","
                + "\"active\":true,"
                + "\"manuallyCorrelated\":false,"
                + "\"locked\":false,"
                + "\"hasEntitlements\":true,"
                + "\"lastRefresh\":\"2024-06-01T10:00:00Z\","
                + "\"schemas\":[\"urn:ietf:params:scim:schemas:sailpoint:1.0:Account\"]"
                + "}]}";

        Account acc = extract(page).get(0);

        assertEquals("acc-1", acc.getId());
        assertEquals("Alice Smith", acc.getDisplayName());
        assertEquals("cn=alice,dc=corp", acc.getNativeIdentity());
        assertEquals(Boolean.TRUE, acc.getActive());
        assertEquals(Boolean.FALSE, acc.getManuallyCorrelated());
        assertEquals(Boolean.FALSE, acc.getLocked());
        assertEquals("2024-06-01T10:00:00Z", acc.getLastRefresh());
        assertEquals(List.of("urn:ietf:params:scim:schemas:sailpoint:1.0:Account"), acc.getSchemas());
    }

    @Test
    void mapsIdentityReference() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"identity\":{\"displayName\":\"Alice Smith\",\"userName\":\"asmith\","
                + "\"value\":\"id-42\",\"$ref\":\"https://iiq/scim/v2/Users/id-42\"}}]}";

        Account acc = extract(page).get(0);

        assertNotNull(acc.getIdentity());
        assertEquals("Alice Smith", acc.getIdentity().getDisplayName());
        assertEquals("asmith", acc.getIdentity().getUserName());
        assertEquals("id-42", acc.getIdentity().getValue());
        assertEquals("https://iiq/scim/v2/Users/id-42", acc.getIdentity().getRef());
    }

    @Test
    void mapsApplicationReference() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"application\":{\"displayName\":\"EntraTarget\","
                + "\"value\":\"app-7\",\"$ref\":\"https://iiq/scim/v2/Applications/app-7\"}}]}";

        Account acc = extract(page).get(0);

        assertNotNull(acc.getApplication());
        assertEquals("EntraTarget", acc.getApplication().getDisplayName());
        assertEquals("app-7", acc.getApplication().getValue());
        assertEquals("https://iiq/scim/v2/Applications/app-7", acc.getApplication().getRef());
        // Applications carry no userName.
        assertNull(acc.getApplication().getUserName());
    }

    @Test
    void mapsMeta() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"meta\":{\"resourceType\":\"Account\",\"created\":\"2024-01-01T00:00:00Z\","
                + "\"lastModified\":\"2024-02-02T00:00:00Z\","
                + "\"location\":\"https://iiq/scim/v2/Accounts/acc-1\",\"version\":\"W/\\\"3\\\"\"}}]}";

        Account acc = extract(page).get(0);

        assertNotNull(acc.getMeta());
        assertEquals("Account", acc.getMeta().getResourceType());
        assertEquals("2024-01-01T00:00:00Z", acc.getMeta().getCreated());
        assertEquals("2024-02-02T00:00:00Z", acc.getMeta().getLastModified());
        assertEquals("https://iiq/scim/v2/Accounts/acc-1", acc.getMeta().getLocation());
    }

    @Test
    void mapsHasEntitlements() {
        String withTrue = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":"
                + "[{\"id\":\"a\",\"hasEntitlements\":true}]}";
        String withFalse = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":"
                + "[{\"id\":\"a\",\"hasEntitlements\":false}]}";

        assertEquals(Boolean.TRUE, extract(withTrue).get(0).getHasEntitlements());
        assertEquals(Boolean.FALSE, extract(withFalse).get(0).getHasEntitlements());
    }

    @Test
    void preservesApplicationSpecificScalarAttributes() {
        // FlatFile-style scalar attributes must be preserved as scalars.
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"employeeId\":\"E12345\",\"jobTitle\":\"Engineer\",\"department\":\"R&D\"}]}";

        Account acc = extract(page).get(0);

        assertEquals("E12345", acc.getAdditionalAttribute("employeeId").asText());
        assertEquals("Engineer", acc.getAdditionalAttribute("jobTitle").asText());
        assertTrue(acc.getAdditionalAttribute("employeeId").isValueNode());
        // Core fields must NOT leak into additional attributes.
        assertNull(acc.getAdditionalAttribute("id"));
    }

    @Test
    void preservesArrayOfScalars() {
        // Multi-valued attribute must stay an array, not be flattened.
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"email\":[\"a@company.com\",\"b@company.com\"]}]}";

        Account acc = extract(page).get(0);
        JsonNode email = acc.getAdditionalAttribute("email");

        assertNotNull(email);
        assertTrue(email.isArray());
        assertEquals(2, email.size());
        assertEquals("a@company.com", email.get(0).asText());
        assertEquals("b@company.com", email.get(1).asText());
    }

    @Test
    void preservesNestedObject() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"managerInfo\":{\"id\":\"m-1\",\"name\":\"Big Boss\"}}]}";

        Account acc = extract(page).get(0);
        JsonNode manager = acc.getAdditionalAttribute("managerInfo");

        assertNotNull(manager);
        assertTrue(manager.isObject());
        assertEquals("m-1", manager.get("id").asText());
        assertEquals("Big Boss", manager.get("name").asText());
    }

    @Test
    void preservesArrayOfObjects() {
        // Groups as an array of {id,name} objects must be preserved completely,
        // including ids and every element.
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"groups\":[{\"id\":\"group-1\",\"name\":\"Admins\"},"
                + "{\"id\":\"group-2\",\"name\":\"Users\"}]}]}";

        Account acc = extract(page).get(0);
        JsonNode groups = acc.getAdditionalAttribute("groups");

        assertNotNull(groups);
        assertTrue(groups.isArray());
        assertEquals(2, groups.size());
        assertEquals("group-1", groups.get(0).get("id").asText());
        assertEquals("Admins", groups.get(0).get("name").asText());
        assertEquals("group-2", groups.get(1).get("id").asText());
        assertEquals("Users", groups.get(1).get("name").asText());
    }

    @Test
    void preservesUnknownFieldsVerbatim() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-1\","
                + "\"someFutureField\":\"keep-me\","
                + "\"proxyAddresses\":[\"SMTP:a@x.com\",\"smtp:b@x.com\"],"
                + "\"objectId\":\"00a6de28-c277\"}]}";

        Account acc = extract(page).get(0);

        assertTrue(acc.hasAdditionalAttributes());
        assertEquals("keep-me", acc.getAdditionalAttribute("someFutureField").asText());
        assertEquals("00a6de28-c277", acc.getAdditionalAttribute("objectId").asText());
        JsonNode proxies = acc.getAdditionalAttribute("proxyAddresses");
        assertTrue(proxies.isArray());
        assertEquals(2, proxies.size());
        assertEquals("SMTP:a@x.com", proxies.get(0).asText());
    }

    @Test
    void toleratesMissingOptionalFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{\"id\":\"acc-min\"}]}";

        Account acc = extract(page).get(0);

        assertEquals("acc-min", acc.getId());
        assertNull(acc.getDisplayName());
        assertNull(acc.getNativeIdentity());
        assertNull(acc.getActive());
        assertNull(acc.getManuallyCorrelated());
        assertNull(acc.getLocked());
        assertNull(acc.getHasEntitlements());
        assertNull(acc.getApplication());
        assertNull(acc.getIdentity());
        assertNull(acc.getMeta());
        assertTrue(acc.getSchemas().isEmpty());
        assertFalse(acc.hasAdditionalAttributes());
    }

    /** A core-only account with a native identity, used by the pagination tests. */
    private static String account(String id, String nativeIdentity) {
        return "{\"id\":\"" + id + "\",\"nativeIdentity\":\"" + nativeIdentity + "\","
                + "\"application\":{\"displayName\":\"EntraTarget\",\"value\":\"app-1\","
                + "\"$ref\":\"https://iiq/scim/v2/Applications/app-1\"},"
                + "\"identity\":{\"displayName\":\"User " + id + "\",\"userName\":\"u-" + id + "\","
                + "\"value\":\"id-" + id + "\",\"$ref\":\"https://iiq/scim/v2/Users/id-" + id + "\"},"
                + "\"hasEntitlements\":true}";
    }
}
