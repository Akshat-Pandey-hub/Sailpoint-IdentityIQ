package com.keyforge.iiq.provisioningtransaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves ProvisioningTransactions from the IdentityIQ classic REST endpoint
 * {@code GET /rest/provisioningTransactions} (verified live). This endpoint requires the session's
 * CSRF token, so the shared {@link IiqSessionClient} session is warmed via {@code warmCsrfToken()}
 * before reading and the token is echoed by the client's {@code get}. Envelope (standard SailPoint
 * REST): {@code {"status":"success","objects":[...],"count":N}} — root {@code objects}, total
 * {@code count}. The server caps a page at 100 rows, so results are paged with {@code start}/
 * {@code limit} until {@code count} rows have been read.
 *
 * <p>Note: the modern {@code ui/rest/provisioningTransactions} route returns HTTP 500 on this
 * instance; the classic {@code /rest/} route is the working authoritative source. Extraction-only:
 * this reads transactions and never writes back to IdentityIQ.
 */
public class ProvisioningTransactionService {

    static final String PATH = "rest/provisioningTransactions";
    static final String ROOT_KEY = "objects";
    static final String TOTAL_KEY = "count";
    static final int PAGE_LIMIT = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProvisioningTransactionService(IiqSessionClient client) {
        this.client = client;
    }

    /** Reads every ProvisioningTransaction, paging until {@code count} rows have been retrieved. */
    public List<ProvisioningTransaction> getAllTransactions() {
        client.warmCsrfToken(); // this endpoint requires the CSRF token
        List<ProvisioningTransaction> out = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (out.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("start", Integer.toString(start));
            query.put("limit", Integer.toString(PAGE_LIMIT));

            String body = client.get(PATH, query);
            List<ProvisioningTransaction> page = parseTransactions(body);
            int declaredTotal = parseTotal(body);
            if (page.isEmpty()) {
                break;
            }
            out.addAll(page);
            if (declaredTotal < 0) {
                break;
            }
            total = declaredTotal;
            start += page.size();
        }
        return out;
    }

    // --- pure parsing (unit-testable; use with a null client) ---------------

    public List<ProvisioningTransaction> parseTransactions(String json) {
        List<ProvisioningTransaction> out = new ArrayList<>();
        JsonNode array = parse(json).get(ROOT_KEY);
        if (array != null && array.isArray()) {
            for (JsonNode t : array) {
                out.add(new ProvisioningTransaction(
                        text(t, "id"),
                        text(t, "name"),
                        text(t, "operation"),
                        text(t, "source"),
                        text(t, "status"),
                        text(t, "statusMessage"),
                        text(t, "type"),
                        text(t, "typeMessage"),
                        text(t, "integration"),
                        text(t, "identityName"),
                        text(t, "identityDisplayName"),
                        text(t, "applicationName"),
                        text(t, "nativeIdentity"),
                        text(t, "accountDisplayName"),
                        text(t, "created"),
                        text(t, "modified"),
                        text(t, "lastRetry"),
                        text(t, "ticketId"),
                        bool(t, "retry"),
                        integer(t, "retryCount"),
                        bool(t, "timedOut"),
                        bool(t, "forced"),
                        bool(t, "forceable"),
                        text(t, "result"),
                        text(t, "accessRequestId"),
                        text(t, "certificationName")));
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
            throw new IiqApiException("Failed to parse ProvisioningTransactions response from " + PATH, e);
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

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.asBoolean() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asInt() : null;
    }
}
