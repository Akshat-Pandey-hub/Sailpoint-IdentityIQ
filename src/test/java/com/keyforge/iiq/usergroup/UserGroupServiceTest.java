package com.keyforge.iiq.usergroup;

import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.UserGroup;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link UserGroupService} calls the three IdentityIQ Group Configuration
 * data sources, parses the real grid JSON shapes captured live on IdentityIQ 8.4
 * ({@code {"workgroups":[...],"totalCount":N}} etc.), keeps each category separate,
 * paginates, preserves the complete raw record, and reports per-source status.
 */
class UserGroupServiceTest {

    /** The real array key each endpoint returns (verified live on IdentityIQ 8.4). */
    private static String keyFor(String path) {
        if (UserGroupService.WORKGROUPS_PATH.equals(path)) return "workgroups";
        if (UserGroupService.POPULATIONS_PATH.equals(path)) return "populations";
        return "groups";
    }

    /** Routes canned bodies per endpoint path; supports ExtJS start/limit pagination. */
    private static IiqApiClient router(Map<String, String> bodyByPath) {
        return new IiqApiClient() {
            @Override
            public String get(String path, Map<String, String> query) {
                String body = bodyByPath.get(path);
                if (body == null) {
                    return "{\"totalCount\":0,\"" + keyFor(path) + "\":[]}";
                }
                // Second/next pages return empty so pagination terminates.
                String start = query == null ? null : query.get("start");
                if (start != null && !"0".equals(start)) {
                    return "{\"totalCount\":" + totalOf(body) + ",\"" + keyFor(path) + "\":[]}";
                }
                return body;
            }
        };
    }

    private static String totalOf(String body) {
        int i = body.indexOf("\"totalCount\":");
        return i < 0 ? "0" : body.substring(i + 13).split("[,}]")[0].trim();
    }

    /** Builds a grid response with the real endpoint-specific array key. */
    private static String grid(String key, int total, String... objects) {
        return "{\"" + key + "\":[" + String.join(",", objects) + "],\"totalCount\":" + total + "}";
    }

    private static Map<String, String> only(String path, String body) {
        Map<String, String> m = new HashMap<>();
        m.put(path, body);
        return m;
    }

    private static UserGroup byType(List<UserGroup> groups, String type) {
        return groups.stream().filter(g -> type.equals(g.getType())).findFirst().orElseThrow();
    }

    @Test
    void parsesAllThreeSourcesAndKeepsCategoriesSeparate() {
        Map<String, String> bodies = new HashMap<>();
        bodies.put(UserGroupService.WORKGROUPS_PATH, grid("workgroups", 1,
                "{\"id\":\"wg-1\",\"name\":\"AttributeSyncWorkGroup\",\"description\":\"sync\",\"modified\":\"5/28/25, 6:47 AM\"}"));
        bodies.put(UserGroupService.POPULATIONS_PATH, grid("populations", 1,
                "{\"id\":\"pop-1\",\"name\":\"High Risk\"}"));
        bodies.put(UserGroupService.GROUPS_PATH, grid("groups", 1,
                "{\"id\":\"grp-1\",\"name\":\"Engineering\"}"));

        UserGroupService.Result r = new UserGroupService(router(bodies)).extractAll();
        assertEquals(3, r.getUserGroups().size());
        assertFalse(r.anyEndpointFailed());

        assertEquals("Workgroup", byType(r.getUserGroups(), "Workgroup").getType());
        assertEquals("AttributeSyncWorkGroup", byType(r.getUserGroups(), "Workgroup").getName());
        assertEquals("Population", byType(r.getUserGroups(), "Population").getType());
        assertEquals("Group", byType(r.getUserGroups(), "Group").getType());
    }

    @Test
    void categoryComesFromEndpointNotRecordContent() {
        // Even if a record carries its own "type", the endpoint decides the category.
        String rec = "{\"id\":\"wg-1\",\"name\":\"X\",\"type\":\"somethingElse\"}";
        UserGroup wg = new UserGroupService(router(only(UserGroupService.WORKGROUPS_PATH, grid("workgroups", 1, rec))))
                .extractAll().getUserGroups().get(0);
        assertEquals("Workgroup", wg.getType());
    }

    @Test
    void paginatesUntilAllRecordsRead() {
        // Page 1 returns 2 of 3; page 2 returns the third. The declared total drives the loop.
        IiqApiClient paging = new IiqApiClient() {
            @Override
            public String get(String path, Map<String, String> query) {
                if (!UserGroupService.WORKGROUPS_PATH.equals(path)) {
                    return "{\"totalCount\":0,\"" + keyFor(path) + "\":[]}";
                }
                String start = query.get("start");
                if ("0".equals(start)) {
                    return grid("workgroups", 3, "{\"id\":\"a\",\"name\":\"A\"}", "{\"id\":\"b\",\"name\":\"B\"}");
                }
                return grid("workgroups", 3, "{\"id\":\"c\",\"name\":\"C\"}");
            }
        };
        UserGroupService.Result r = new UserGroupService(paging).extractAll();
        assertEquals(3, r.getUserGroups().size());
    }

    @Test
    void emptySourceReportsZeroAndContinues() {
        Map<String, String> bodies = new HashMap<>();
        bodies.put(UserGroupService.WORKGROUPS_PATH, grid("workgroups", 0));
        bodies.put(UserGroupService.GROUPS_PATH, grid("groups", 1, "{\"id\":\"g\",\"name\":\"G\"}"));
        UserGroupService.Result r = new UserGroupService(router(bodies)).extractAll();

        assertEquals(1, r.getUserGroups().size());
        assertTrue(String.join(" ", r.getSourceStatus()).contains("Workgroup"));
        assertFalse(r.anyEndpointFailed());
    }

    @Test
    void toleratesMissingOptionalFields() {
        String minimal = "{\"name\":\"OnlyName\"}"; // no id, description, owner, members
        UserGroup g = new UserGroupService(router(only(UserGroupService.GROUPS_PATH, grid("groups", 1, minimal))))
                .extractAll().getUserGroups().get(0);
        assertEquals("OnlyName", g.getName());
        assertFalse(g.isMembersProvided());
        assertTrue(g.getMembers().isEmpty());
    }

    @Test
    void preservesCompleteRawRecord() {
        // Exactly the shape a real workgroup record has, plus an extra nested field.
        String rec = "{\"name\":\"Team\",\"description\":\"spadminq\","
                + "\"modified\":\"6/29/26, 3:57 AM\",\"id\":\"wg-1\",\"custom\":{\"a\":[1,2]}}";
        UserGroup g = new UserGroupService(router(only(UserGroupService.WORKGROUPS_PATH, grid("workgroups", 1, rec))))
                .extractAll().getUserGroups().get(0);
        assertEquals("spadminq", g.getAdditionalAttributes().path("description").asText());
        assertEquals("6/29/26, 3:57 AM", g.getAdditionalAttributes().path("modified").asText());
        assertTrue(g.getAdditionalAttributes().path("custom").path("a").isArray());
    }

    @Test
    void parsesMembersWhenPresent() {
        String rec = "{\"id\":\"wg-1\",\"name\":\"Team\",\"members\":["
                + "{\"id\":\"u-1\",\"name\":\"Alice\"},{\"id\":\"u-2\",\"name\":\"Bob\"}]}";
        UserGroup g = new UserGroupService(router(only(UserGroupService.WORKGROUPS_PATH, grid("workgroups", 1, rec))))
                .extractAll().getUserGroups().get(0);
        assertTrue(g.isMembersProvided());
        assertEquals(2, g.getMembers().size());
        assertEquals("u-1", g.getMembers().get(0).getValue());
    }

    @Test
    void endpointErrorIsReportedNotTreatedAsZero() {
        IiqApiClient failingWorkgroups = new IiqApiClient() {
            @Override
            public String get(String path, Map<String, String> query) {
                if (UserGroupService.WORKGROUPS_PATH.equals(path)) {
                    throw new IiqApiException("boom", 500);
                }
                return grid(keyFor(path), 1, "{\"id\":\"g\",\"name\":\"G\"}"); // other sources succeed
            }
        };
        UserGroupService.Result r = new UserGroupService(failingWorkgroups).extractAll();
        assertTrue(r.anyEndpointFailed());
        assertTrue(String.join(" ", r.getSourceStatus()).contains("FAILED"));
        assertEquals(2, r.getUserGroups().size()); // populations + groups still extracted
    }
}
