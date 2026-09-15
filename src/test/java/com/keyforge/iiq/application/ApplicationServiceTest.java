package com.keyforge.iiq.application;

import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.model.Application;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationServiceTest {

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
    void retrievesAllApplicationsFromSinglePage() {
        String page = "{\"totalResults\":2,\"startIndex\":1,\"Resources\":["
                + app("a1", "FlatFile", "FlatFile")
                + "," + app("a2", "EntraTarget", "Azure Active Directory")
                + "]}";

        FakeClient client = new FakeClient(List.of(page));
        List<Application> apps = new ApplicationService(client).getAllApplications();

        assertEquals(2, apps.size());
        assertEquals(List.of("a1", "a2"), apps.stream().map(Application::getId).toList());
        assertEquals(1, client.requestedStartIndexes.size()); // one page fetched
    }

    @Test
    void retrievesAllApplicationsAcrossMultiplePages() {
        // totalResults = 3, first page of 2, second page of 1.
        String page1 = "{\"totalResults\":3,\"startIndex\":1,\"itemsPerPage\":2,\"Resources\":["
                + app("a1", "FlatFile", "FlatFile")
                + "," + app("a2", "FlatFile-HR-Source", "DelimitedFile")
                + "]}";
        String page2 = "{\"totalResults\":3,\"startIndex\":3,\"itemsPerPage\":2,\"Resources\":["
                + app("a3", "EntraAuth", "Azure Active Directory")
                + "]}";

        FakeClient client = new FakeClient(List.of(page1, page2));
        List<Application> apps = new ApplicationService(client).getAllApplications();

        assertEquals(3, apps.size());
        assertEquals(List.of("a1", "a2", "a3"), apps.stream().map(Application::getId).toList());
        // startIndex advances by the number of resources actually returned.
        assertEquals(List.of("1", "3"), client.requestedStartIndexes);
    }

    @Test
    void handlesEmptyResources() {
        // Server claims a total but returns nothing: must not loop forever.
        String onlyPage = "{\"totalResults\":6,\"startIndex\":1,\"Resources\":[]}";
        FakeClient client = new FakeClient(List.of(onlyPage));

        List<Application> apps = new ApplicationService(client).getAllApplications();

        assertTrue(apps.isEmpty());
        assertEquals(1, client.requestedStartIndexes.size());
    }

    @Test
    void mapsObservedApplicationFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"7f00-app-1\","
                + "\"name\":\"corp directory-LDAP-Target\","
                + "\"type\":\"LDAP\","
                + "\"owner\":{\"value\":\"owner-123\",\"$ref\":\"https://iiq/scim/v2/Users/owner-123\","
                + "\"displayName\":\"The Administrator\"},"
                + "\"applicationSchemas\":[{"
                + "\"type\":\"account\",\"value\":\"schema-9\",\"$ref\":\"https://iiq/scim/v2/Schemas/schema-9\"}],"
                + "\"meta\":{\"resourceType\":\"Application\",\"created\":\"2024-01-02T03:04:05Z\","
                + "\"lastModified\":\"2024-05-06T07:08:09Z\","
                + "\"location\":\"https://iiq/scim/v2/Applications/7f00-app-1\",\"version\":\"W/\\\"1\\\"\"}"
                + "}]}";

        FakeClient client = new FakeClient(List.of(page));
        Application app = new ApplicationService(client).getAllApplications().get(0);

        assertEquals("7f00-app-1", app.getId());
        assertEquals("corp directory-LDAP-Target", app.getName());
        assertEquals("LDAP", app.getType());

        assertNotNull(app.getOwner());
        assertEquals("owner-123", app.getOwner().getValue());
        assertEquals("https://iiq/scim/v2/Users/owner-123", app.getOwner().getRef());
        assertEquals("The Administrator", app.getOwner().getDisplayName());

        assertEquals(1, app.getApplicationSchemaCount());
        Application.ApplicationSchema schema = app.getApplicationSchemas().get(0);
        assertEquals("account", schema.getType());
        assertEquals("schema-9", schema.getValue());
        assertEquals("https://iiq/scim/v2/Schemas/schema-9", schema.getRef());

        assertNotNull(app.getMeta());
        assertEquals("Application", app.getMeta().getResourceType());
        assertEquals("2024-01-02T03:04:05Z", app.getMeta().getCreated());
        assertEquals("2024-05-06T07:08:09Z", app.getMeta().getLastModified());
    }

    @Test
    void toleratesMissingOptionalFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"a-min\",\"name\":\"FlatFile\"}]}";
        FakeClient client = new FakeClient(List.of(page));

        Application app = new ApplicationService(client).getAllApplications().get(0);

        assertEquals("a-min", app.getId());
        assertEquals("FlatFile", app.getName());
        assertNull(app.getType());
        assertNull(app.getOwner());
        assertNull(app.getMeta());
        assertEquals(0, app.getApplicationSchemaCount());
    }

    @Test
    void additionalAttributesPreserveDescriptionsSchemasAndUnknownFields() {
        String page = "{\"totalResults\":1,\"startIndex\":1,\"Resources\":[{"
                + "\"id\":\"a1\",\"name\":\"FlatFile\",\"type\":\"DelimitedFile\","
                + "\"descriptions\":{\"en_US\":\"HR file\"},"
                + "\"schemas\":[\"urn:ietf:params:scim:schemas:sailpoint:1.0:Application\"],"
                + "\"newField\":\"hello\"}]}";

        FakeClient client = new FakeClient(List.of(page));
        Application app = new ApplicationService(client).getAllApplications().get(0);

        // Un-modelled / non-column fields are preserved verbatim...
        assertEquals("HR file", app.getAdditionalAttributes().path("descriptions").path("en_US").asText());
        assertTrue(app.getAdditionalAttributes().path("schemas").isArray());
        assertEquals("hello", app.getAdditionalAttributes().path("newField").asText());
        // ...and the typed/captured fields are not duplicated in the remainder.
        assertFalse(app.getAdditionalAttributes().has("id"));
        assertFalse(app.getAdditionalAttributes().has("name"));
        assertFalse(app.getAdditionalAttributes().has("type"));
    }

    private static String app(String id, String name, String type) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"type\":\"" + type + "\","
                + "\"owner\":{\"value\":\"o-" + id + "\",\"displayName\":\"Owner " + id + "\"},"
                + "\"applicationSchemas\":[{\"type\":\"account\",\"value\":\"s-" + id + "\","
                + "\"$ref\":\"https://iiq/scim/v2/Schemas/s-" + id + "\"}]}";
    }
}
