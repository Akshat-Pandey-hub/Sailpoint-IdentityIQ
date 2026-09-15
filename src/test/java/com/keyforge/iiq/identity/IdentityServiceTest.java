package com.keyforge.iiq.identity;

import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.model.Identity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityServiceTest {

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
            String startIndex = queryParams.get("startIndex");
            requestedStartIndexes.add(startIndex);
            int idx = Integer.parseInt(startIndex);
            // startIndex is 1-based; map to a zero-based page cursor.
            int served = requestedStartIndexes.size() - 1;
            if (served >= pages.size()) {
                // Empty page as a defensive default.
                return "{\"totalResults\":0,\"startIndex\":" + idx + ",\"Resources\":[]}";
            }
            return pages.get(served);
        }
    }

    @Test
    void retrievesAllUsersAcrossMultiplePages() {
        // totalResults = 5, two full pages of 2 and a final page of 1.
        String page1 = "{\"totalResults\":5,\"startIndex\":1,\"itemsPerPage\":2,\"Resources\":["
                + user("1", "alice") + "," + user("2", "bob") + "]}";
        String page2 = "{\"totalResults\":5,\"startIndex\":3,\"itemsPerPage\":2,\"Resources\":["
                + user("3", "carol") + "," + user("4", "dave") + "]}";
        String page3 = "{\"totalResults\":5,\"startIndex\":5,\"itemsPerPage\":2,\"Resources\":["
                + user("5", "erin") + "]}";

        FakeClient client = new FakeClient(List.of(page1, page2, page3));
        IdentityService service = new IdentityService(client);

        List<Identity> identities = service.getAllIdentities();

        assertEquals(5, identities.size());
        assertEquals(List.of("1", "2", "3", "4", "5"),
                identities.stream().map(Identity::getId).toList());
        // Requested pages should advance by the number of resources returned.
        assertEquals(List.of("1", "3", "5"), client.requestedStartIndexes);
    }

    @Test
    void stopsWhenResourcesEmptyEvenIfTotalClaimsMore() {
        // Server lies (totalResults=99) but returns no resources: must not loop forever.
        String onlyPage = "{\"totalResults\":99,\"startIndex\":1,\"Resources\":[]}";
        FakeClient client = new FakeClient(List.of(onlyPage));
        IdentityService service = new IdentityService(client);

        List<Identity> identities = service.getAllIdentities();

        assertTrue(identities.isEmpty());
        assertEquals(1, client.requestedStartIndexes.size());
    }

    @Test
    void mapsObservedFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"abc\","
                + "\"userName\":\"alice.smith\","
                + "\"displayName\":\"Alice Smith\","
                + "\"active\":true,"
                + "\"name\":{\"givenName\":\"Alice\",\"familyName\":\"Smith\"},"
                + "\"emails\":[{\"value\":\"old@x.com\",\"primary\":false},"
                + "{\"value\":\"alice@x.com\",\"primary\":true}]"
                + "}]}";

        FakeClient client = new FakeClient(List.of(page));
        Identity identity = new IdentityService(client).getAllIdentities().get(0);

        assertEquals("abc", identity.getId());
        assertEquals("alice.smith", identity.getUserName());
        assertEquals("Alice Smith", identity.getDisplayName());
        assertEquals(Boolean.TRUE, identity.getActive());
        assertEquals("Alice", identity.getFirstName());
        assertEquals("Smith", identity.getLastName());
        // Primary email is preferred over the first listed.
        assertEquals("alice@x.com", identity.getEmail());
        assertEquals(0, identity.getAccountReferenceCount());
    }

    @Test
    void preservesAccountReferencesFromExtension() {
        // Account references nested inside a SailPoint-style extension object.
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"u1\",\"userName\":\"bob\","
                + "\"urn:ietf:params:scim:schemas:sailpoint:1.0:User\":{"
                + "\"accounts\":[{\"value\":\"acc-1\"},{\"value\":\"acc-2\"}]}"
                + "}]}";

        FakeClient client = new FakeClient(List.of(page));
        Identity identity = new IdentityService(client).getAllIdentities().get(0);

        assertEquals(2, identity.getAccountReferenceCount());
        assertEquals("acc-1", identity.getAccountReferences().get(0).path("value").asText());
        assertEquals("acc-2", identity.getAccountReferences().get(1).path("value").asText());
    }

    @Test
    void toleratesMissingOptionalFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"u2\",\"userName\":\"minimal\"}]}";
        FakeClient client = new FakeClient(List.of(page));
        Identity identity = new IdentityService(client).getAllIdentities().get(0);

        assertEquals("minimal", identity.getUserName());
        assertNull(identity.getDisplayName());
        assertNull(identity.getActive());
        assertNull(identity.getEmail());
        assertEquals(0, identity.getAccountReferenceCount());
    }

    @Test
    void preservesUserExtensionEntitlementReferences() {
        // The SailPoint User extension carries an entitlements array; it must be
        // preserved verbatim without breaking the existing accountReferences behaviour.
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"u1\",\"userName\":\"alice\","
                + "\"urn:ietf:params:scim:schemas:sailpoint:1.0:User\":{"
                + "\"entitlements\":[{\"application\":\"EntraAuth\",\"accountName\":\"alice\","
                + "\"display\":\"G1\",\"type\":\"Entitlement\",\"value\":\"groups\","
                + "\"$ref\":\"https://iiq/scim/v2/Entitlements/e1\"}]}"
                + "}]}";

        FakeClient client = new FakeClient(List.of(page));
        Identity identity = new IdentityService(client).getAllIdentities().get(0);

        assertEquals(1, identity.getEntitlementReferenceCount());
        assertEquals("G1", identity.getEntitlementReferences().get(0).path("display").asText());
        assertEquals("https://iiq/scim/v2/Entitlements/e1",
                identity.getEntitlementReferences().get(0).path("$ref").asText());
    }

    @Test
    void identitiesWithoutExtensionHaveNoEntitlementReferences() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[" + user("1", "bob") + "]}";
        FakeClient client = new FakeClient(List.of(page));
        Identity identity = new IdentityService(client).getAllIdentities().get(0);

        assertEquals(0, identity.getEntitlementReferenceCount());
    }

    @Test
    void extendedAttributesPreserveCustomFieldsAndExcludeMappedOnes() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"u1\",\"userName\":\"alice\",\"displayName\":\"Alice\",\"active\":true,"
                + "\"name\":{\"givenName\":\"Alice\",\"familyName\":\"Smith\"},"
                + "\"emails\":[{\"value\":\"a@x.com\",\"primary\":true}],"
                + "\"schemas\":[\"urn:x\"],\"meta\":{\"resourceType\":\"User\"},"
                + "\"department\":\"IT\",\"customField\":{\"k\":\"v\"}}]}";

        FakeClient client = new FakeClient(List.of(page));
        Identity identity = new IdentityService(client).getAllIdentities().get(0);

        // Everything except the fully-captured columns is preserved verbatim, so the
        // complete name/emails/meta/schemas/active and any custom field survive.
        assertEquals("IT", identity.getExtendedAttributes().path("department").asText());
        assertEquals("v", identity.getExtendedAttributes().path("customField").path("k").asText());
        assertTrue(identity.getExtendedAttributes().has("name"));
        assertTrue(identity.getExtendedAttributes().has("emails"));
        assertTrue(identity.getExtendedAttributes().has("active"));
        assertTrue(identity.getExtendedAttributes().has("schemas"));
        assertEquals("User", identity.getExtendedAttributes().path("meta").path("resourceType").asText());
        // Only the fully-captured columns are excluded (no duplication of those).
        assertFalse(identity.getExtendedAttributes().has("id"));
        assertFalse(identity.getExtendedAttributes().has("userName"));
        assertFalse(identity.getExtendedAttributes().has("displayName"));
    }

    @Test
    void extendedAttributesEmptyWhenOnlyCapturedColumnsPresent() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"1\",\"userName\":\"bob\",\"displayName\":\"Bob\"}]}";
        FakeClient client = new FakeClient(List.of(page));
        Identity identity = new IdentityService(client).getAllIdentities().get(0);

        assertTrue(identity.getExtendedAttributes().isEmpty());
    }

    private static String user(String id, String userName) {
        return "{\"id\":\"" + id + "\",\"userName\":\"" + userName + "\",\"active\":true}";
    }
}
