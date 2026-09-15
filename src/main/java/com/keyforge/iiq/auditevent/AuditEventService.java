package com.keyforge.iiq.auditevent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves AuditEvents from the IdentityIQ Advanced-Analytics audit-search datasource
 * {@code GET /analyze/audit/auditDataSource.json} (verified live). This classic {@code .json}
 * datasource requires the session's CSRF token (redirects to login / errors without it), so the
 * shared {@link IiqSessionClient} session is warmed via {@code warmCsrfToken()} before reading and
 * the token is echoed by the client's {@code get}. Envelope (verified from the datasource's own
 * {@code metaData}): {@code {"totalCount":N,"results":[{id,action,source,target,created}]}} — root
 * {@code results}, total {@code totalCount}, id {@code id}. The server caps a page at 100 rows
 * regardless of the requested {@code limit}, so results are paged with {@code start}/{@code limit}
 * until {@code totalCount} rows have been read.
 *
 * <p>Extraction-only: this reads the audit search and never writes back to IdentityIQ.
 */
public class AuditEventService {

    static final String AUDIT_PATH = "analyze/audit/auditDataSource.json";
    static final String ROOT_KEY = "results";
    static final String TOTAL_KEY = "totalCount";
    /** The datasource caps a page at 100 rows server-side; request exactly that. */
    static final int PAGE_LIMIT = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditEventService(IiqSessionClient client) {
        this.client = client;
    }

    /** Reads every AuditEvent, paging until {@code totalCount} rows have been retrieved. */
    public List<AuditEvent> getAllAuditEvents() {
        client.warmCsrfToken(); // this datasource requires the CSRF token
        List<AuditEvent> events = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (events.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + AUDIT_PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("start", Integer.toString(start));
            query.put("limit", Integer.toString(PAGE_LIMIT));

            String body = client.get(AUDIT_PATH, query);
            List<AuditEvent> page = parseAuditEvents(body);
            int declaredTotal = parseTotal(body);
            if (page.isEmpty()) {
                break;
            }
            events.addAll(page);
            if (declaredTotal < 0) {
                break;
            }
            total = declaredTotal;
            start += page.size();
        }
        return events;
    }

    // --- pure parsing (unit-testable; use with a null client) ---------------

    public List<AuditEvent> parseAuditEvents(String json) {
        List<AuditEvent> out = new ArrayList<>();
        JsonNode array = parse(json).get(ROOT_KEY);
        if (array != null && array.isArray()) {
            for (JsonNode r : array) {
                out.add(new AuditEvent(
                        text(r, "id"),
                        text(r, "action"),
                        text(r, "source"),
                        text(r, "target"),
                        text(r, "created")));
            }
        }
        return out;
    }

    public int parseTotal(String json) {
        JsonNode v = parse(json).get(TOTAL_KEY);
        return v != null && v.isNumber() ? v.asInt() : -1;
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse AuditEvents response from " + AUDIT_PATH, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
