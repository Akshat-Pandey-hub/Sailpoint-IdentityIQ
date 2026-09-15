package com.keyforge.iiq.workgroupmember;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retrieves Workgroup → Identity membership from IdentityIQ using the exact request the
 * Edit-Workgroup UI makes (verified live on this instance).
 *
 * <p>The members grid ({@code define/groups/workgroupMembersDataSource.json}) is
 * <b>session-scoped</b>: it returns the members of the "current workgroup" the session is
 * editing, not a workgroup passed as a parameter. So, per workgroup, this reproduces the UI
 * flow: GET {@code groups.jsf} for a fresh JSF ViewState, POST the {@code editForm} postback
 * with {@code currentWorkgroupObjectId} (which navigates to {@code editWorkgroup.jsf} and sets
 * the session's current workgroup), then GET the members grid. Membership is taken ONLY from
 * this authoritative source — never inferred from accounts, entitlements, populations, groups,
 * identity attributes, work items, or names.
 */
public class WorkgroupMemberService {

    static final String GROUPS_PAGE = "define/groups/groups.jsf";
    static final String MEMBERS_PATH = "define/groups/workgroupMembersDataSource.json";

    static final String EDIT_FORM = "editForm";
    static final String FIELD_VIEWSTATE = "javax.faces.ViewState";
    static final String FIELD_WG_OBJECT_ID = "editForm:currentWorkgroupObjectId";
    static final String FIELD_EDIT_BUTTON = "editForm:editWorkgroupButton";

    static final String MEMBERS_ARRAY_KEY = "workgroupMembers";
    static final String TOTAL_KEY = "totalCount";

    static final int PAGE_LIMIT = 25;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkgroupMemberService(IiqSessionClient client) {
        this.client = client;
    }

    /** Result of a membership extraction run across a set of workgroups. */
    public static final class Result {
        private final List<WorkgroupMembership> memberships;
        private final List<String> status;
        private final boolean anyFailed;
        private final int workgroupsProcessed;
        private final int workgroupsWithZeroMembers;

        Result(List<WorkgroupMembership> memberships, List<String> status, boolean anyFailed,
               int workgroupsProcessed, int workgroupsWithZeroMembers) {
            this.memberships = memberships;
            this.status = status;
            this.anyFailed = anyFailed;
            this.workgroupsProcessed = workgroupsProcessed;
            this.workgroupsWithZeroMembers = workgroupsWithZeroMembers;
        }

        public List<WorkgroupMembership> getMemberships() {
            return memberships;
        }

        public List<String> getStatus() {
            return status;
        }

        public boolean anyFailed() {
            return anyFailed;
        }

        public int getWorkgroupsProcessed() {
            return workgroupsProcessed;
        }

        public int getWorkgroupsWithZeroMembers() {
            return workgroupsWithZeroMembers;
        }
    }

    /** Extracts members for each supplied workgroup id (each independent; failures are reported). */
    public Result extractAll(List<String> workgroupIds) {
        List<WorkgroupMembership> all = new ArrayList<>();
        List<String> status = new ArrayList<>();
        boolean anyFailed = false;
        int processed = 0;
        int zero = 0;

        for (String workgroupId : workgroupIds == null ? List.<String>of() : workgroupIds) {
            processed++;
            try {
                selectWorkgroup(workgroupId);
                List<WorkgroupMembership> members = fetchMembers(workgroupId);
                all.addAll(members);
                if (members.isEmpty()) {
                    zero++;
                }
                status.add(workgroupId + ": " + members.size() + " member(s)");
            } catch (IiqApiException e) {
                anyFailed = true;
                String reason = e.hasStatusCode() ? "HTTP " + e.getStatusCode() : e.getMessage();
                status.add(workgroupId + ": FAILED — " + reason);
            }
        }
        return new Result(all, status, anyFailed, processed, zero);
    }

    /** Reproduces the UI's editWorkgroup postback so the session's "current workgroup" is set. */
    private void selectWorkgroup(String workgroupId) {
        String groupsHtml = client.get(GROUPS_PAGE, Map.of());
        String viewState = parseViewState(groupsHtml);
        if (viewState == null) {
            throw new IiqApiException("Could not read the JSF ViewState from " + GROUPS_PAGE
                    + " — cannot open the Edit Workgroup members grid.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put(EDIT_FORM, EDIT_FORM);
        form.put(FIELD_VIEWSTATE, viewState);
        form.put(FIELD_WG_OBJECT_ID, workgroupId);
        form.put(FIELD_EDIT_BUTTON, "");
        client.postForm(GROUPS_PAGE, form);
    }

    /** Pages the (session-scoped) members grid for the currently-selected workgroup. */
    private List<WorkgroupMembership> fetchMembers(String workgroupId) {
        List<WorkgroupMembership> out = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (out.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " member pages for workgroup " + workgroupId);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("_dc", Long.toString(System.currentTimeMillis()));
            query.put("start", Integer.toString(start));
            query.put("limit", Integer.toString(PAGE_LIMIT));
            query.put("page", Integer.toString(start / PAGE_LIMIT + 1));

            String body = client.get(MEMBERS_PATH, query);
            List<WorkgroupMembership> page = parseMembers(body, workgroupId);
            int declaredTotal = parseTotal(body);
            if (page.isEmpty()) {
                break;
            }
            out.addAll(page);
            if (declaredTotal < 0) {
                break; // no total -> single page
            }
            total = declaredTotal;
            start += page.size();
        }
        return out;
    }

    // --- pure parsing (unit-testable, no network) ---------------------------

    /** Parses the members grid JSON into memberships for the given workgroup. */
    public List<WorkgroupMembership> parseMembers(String json, String workgroupId) {
        List<WorkgroupMembership> members = new ArrayList<>();
        JsonNode root = parse(json);
        JsonNode array = root.get(MEMBERS_ARRAY_KEY);
        if (array != null && array.isArray()) {
            for (JsonNode m : array) {
                String id = text(m, "id");
                if (id == null) {
                    continue; // no identity id -> not a usable membership; never fabricate
                }
                members.add(new WorkgroupMembership(
                        workgroupId, id, text(m, "name"), text(m, "firstname"), text(m, "lastname")));
            }
        }
        return members;
    }

    int parseTotal(String json) {
        JsonNode root = parse(json);
        JsonNode v = root.get(TOTAL_KEY);
        if (v != null && v.isNumber()) {
            return v.asInt();
        }
        if (v != null && v.isTextual()) {
            try {
                return Integer.parseInt(v.asText().trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    /** Reads the JSF {@code javax.faces.ViewState} value from a page, tolerant of attribute order. */
    public static String parseViewState(String html) {
        if (html == null) {
            return null;
        }
        Matcher m = Pattern.compile("name=\"javax\\.faces\\.ViewState\"[^>]*?value=\"([^\"]*)\"").matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        m = Pattern.compile("value=\"([^\"]*)\"[^>]*?name=\"javax\\.faces\\.ViewState\"").matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse workgroup members response from " + MEMBERS_PATH, e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
