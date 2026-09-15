package com.keyforge.iiq.certification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves certification campaigns (CertificationGroups) from the IdentityIQ classic REST endpoint
 * {@code POST /rest/certificationGroups} (verified live). This endpoint only accepts POST (GET → 405),
 * requires the session CSRF token, and its ExtJS store sends {@code start}/{@code limit} as form
 * fields. Envelope (standard SailPoint REST): {@code {"status":"success","objects":[...],"count":N}}
 * — root {@code objects}, total {@code count}. Results are paged with {@code start}/{@code limit}
 * until {@code count} rows have been read.
 *
 * <p>Note: the modern {@code ui/rest/certificationGroups/*} drill-in routes return HTTP 500 on this
 * instance and there is no certification plugin installed, so this classic group endpoint is the
 * working authoritative source for campaign-level data. Extraction-only: never writes back and never
 * performs a certification action.
 */
public class CertificationCampaignService {

    static final String PATH = "rest/certificationGroups";
    static final String ROOT_KEY = "objects";
    static final String TOTAL_KEY = "count";
    static final int PAGE_LIMIT = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CertificationCampaignService(IiqSessionClient client) {
        this.client = client;
    }

    /** Reads every certification campaign, paging until {@code count} rows have been retrieved. */
    public List<CertificationCampaign> getAllCampaigns() {
        List<CertificationCampaign> out = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (out.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + PATH);
            }
            Map<String, String> form = new LinkedHashMap<>();
            form.put("start", Integer.toString(start));
            form.put("limit", Integer.toString(PAGE_LIMIT));

            String body = client.postFormForJson(PATH, form);
            List<CertificationCampaign> page = parseCampaigns(body);
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

    public List<CertificationCampaign> parseCampaigns(String json) {
        List<CertificationCampaign> out = new ArrayList<>();
        JsonNode array = parse(json).get(ROOT_KEY);
        if (array != null && array.isArray()) {
            for (JsonNode c : array) {
                JsonNode tags = c.path("tags");
                out.add(new CertificationCampaign(
                        text(c, "id"),
                        text(c, "name"),
                        text(c, "ownerDisplayName"),
                        text(c, "status"),
                        text(c, "percentComplete"),
                        epochMillis(c, "created"),
                        tags.isArray() ? tags.toString() : null));
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
            throw new IiqApiException("Failed to parse certificationGroups response from " + PATH, e);
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

    private static Long epochMillis(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asLong() : null;
    }
}
