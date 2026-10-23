package com.keyforge.nativeload;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Upstream reads of the KF Agent services whose plugin source pages PARENT objects that expand to several response
 * rows (identity → roles, link → entitlement edges, request → items, ...). {@code start}/{@code limit} count response
 * rows, so these services read parent pages in order and stop once the window is filled. The fake plugin pages parents
 * like the real resources ({@code firstRow=start}, {@code resultLimit=limit}, returned-parent counts, source count) and
 * records every call. Filters are passed as an empty map, exactly as the HTTP handler does.
 */
class KfAgentDerivedPagingTest {

    /** Builds one service's envelope for parents [start, start+count), given each parent's expanded row JSON. */
    interface Envelope {
        String build(int start, int count, int limit, int total, String rows, String runId);
    }

    /** Builds expanded row {@code k} of parent {@code i}, carrying {@link #key(int, int)} in the service's id field. */
    interface RowJson {
        String build(int i, int k);
    }

    record Service(String name, String outKey, Envelope envelope, RowJson row, Fetch fetch) {
        @Override
        public String toString() {
            return name;
        }
    }

    interface Fetch {
        List<Map<String, Object>> fetch(Plugin p, Map<String, String> filters, Integer start, Integer limit);
    }

    /** Plugin-like source of {@code parents} parent objects; parent i expands to {@code rowsOf(i)} rows. */
    static final class Plugin {
        final Service svc;
        final int parents;
        final IntUnaryOperator rowsOf;
        final List<int[]> calls = new ArrayList<>();

        Plugin(Service svc, int parents, IntUnaryOperator rowsOf) {
            this.svc = svc;
            this.parents = parents;
            this.rowsOf = rowsOf;
        }

        String fetchPage(int start, int limit, String runId) {
            calls.add(new int[]{start, limit});
            int l = limit <= 0 ? 100 : Math.min(limit, 1000);
            int count = Math.max(0, Math.min(parents, start + l) - start);
            StringBuilder rows = new StringBuilder();
            for (int i = start; i < start + count; i++) {
                for (int k = 0; k < rowsOf.applyAsInt(i); k++) {
                    if (rows.length() > 0) rows.append(',');
                    rows.append(svc.row().build(i, k));
                }
            }
            return svc.envelope().build(start, count, l, parents, rows.toString(), runId);
        }

        String fetchPage(int start, int limit) {
            return fetchPage(start, limit, null);
        }

        /** Every expanded row key in order: the result of the old full scan. */
        List<String> all() {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < parents; i++) {
                for (int k = 0; k < rowsOf.applyAsInt(i); k++) out.add(key(i, k));
            }
            return out;
        }
    }

    static String key(int i, int k) {
        return "p" + i + "r" + k;
    }

    private static String rel(String array, int start, int count, int total, String rows, String runId) {
        String other = array.equals("roleEntitlements") ? "roleHierarchy" : "roleEntitlements";
        return "{\"sourceCount\":" + total + ",\"returnedBundles\":" + count + ",\"extractionRunId\":\"" + runId
                + "\",\"" + array + "\":[" + rows + "],\"" + other + "\":[]}";
    }

    static Stream<Service> services() {
        return Stream.of(
                new Service("account-entitlements", "attribute_value",
                        (s, c, l, t, rows, run) -> "{\"sourceCount\":" + t + ",\"returnedLinks\":" + c + ",\"rows\":[" + rows + "]}",
                        (i, k) -> "{\"linkId\":\"L" + i + "\",\"type\":\"ATTRIBUTE\",\"attributeName\":\"groups\","
                                + "\"attributeValue\":\"" + key(i, k) + "\"}",
                        (p, f, s, l) -> new NativeAccountEntitlementRestService().fetch(p::fetchPage, f, s, l)),
                new Service("identity-roles", "role_id",
                        (s, c, l, t, rows, run) -> "{\"sourceCount\":" + t + ",\"returnedIdentities\":" + c + ",\"rows\":[" + rows + "]}",
                        (i, k) -> "{\"identityId\":\"I" + i + "\",\"roleId\":\"" + key(i, k) + "\"}",
                        (p, f, s, l) -> new NativeIdentityRoleRestService().fetch(p::fetchPage, f, s, l)),
                new Service("policy-constraints", "source_id",
                        (s, c, l, t, rows, run) -> "{\"sourceCount\":" + t + ",\"returnedPolicies\":" + c + ",\"rows\":[" + rows + "]}",
                        (i, k) -> "{\"sourceId\":\"" + key(i, k) + "\",\"policyId\":\"P" + i + "\"}",
                        (p, f, s, l) -> new NativePolicyConstraintRestService().fetch(p::fetchPage, f, s, l)),
                new Service("role-entitlements", "source_role_id",
                        (s, c, l, t, rows, run) -> rel("roleEntitlements", s, c, t, rows, run),
                        (i, k) -> "{\"sourceBundleId\":\"" + key(i, k) + "\"}",
                        (p, f, s, l) -> new NativeRoleEntitlementRestService().fetch(p::fetchPage, f, s, l)),
                new Service("role-hierarchy", "source_role_id",
                        (s, c, l, t, rows, run) -> rel("roleHierarchy", s, c, t, rows, run),
                        (i, k) -> "{\"sourceRoleId\":\"" + key(i, k) + "\",\"relatedRoleId\":\"x\"}",
                        (p, f, s, l) -> new NativeRoleHierarchyRestService().fetch(p::fetchPage, f, s, l)),
                new Service("identity-request-items", "source_id",
                        Requests::envelope,
                        (i, k) -> "{\"sourceId\":\"" + key(i, k) + "\",\"requestSourceId\":\"R" + i + "\"}",
                        (p, f, s, l) -> new NativeIdentityRequestItemRestService().fetch(Requests.of(p, "items"), f, s, l)),
                new Service("identity-request-approvals", "work_item_id",
                        Requests::envelope,
                        (i, k) -> "{\"requestSourceId\":\"R" + i + "\",\"workItemId\":\"" + key(i, k) + "\"}",
                        (p, f, s, l) -> new NativeIdentityRequestApprovalRestService().fetch(Requests.of(p, "approvals"), f, s, l)),
                new Service("provisioning-items", "txn_source_id",
                        Requests::envelope,
                        (i, k) -> "{\"txnSourceId\":\"" + key(i, k) + "\"}",
                        (p, f, s, l) -> new NativeProvisioningItemRestService().fetch(Requests.of(p, "items")::fetchPage, f, s, l)),
                new Service("workgroup-members", "workgroup_id",
                        (s, c, l, t, rows, run) -> "{\"rows\":[" + rows + "],\"nextStart\":" + (s + c) + ",\"done\":" + (c < l) + "}",
                        (i, k) -> "{\"workgroupId\":\"" + key(i, k) + "\",\"identityId\":\"I" + i + "\"}",
                        (p, f, s, l) -> new NativeWorkgroupMemberRestService().fetch(p::fetchPage, f, s, l)));
    }

    /** Request/transaction-shaped sources: one parent row per request, its expanded rows nested in an array. */
    static final class Requests implements NativeIdentityRequestPageSource {
        final Plugin plugin;
        final String array;

        private Requests(Plugin plugin, String array) {
            this.plugin = plugin;
            this.array = array;
        }

        static Requests of(Plugin plugin, String array) {
            return new Requests(plugin, array);
        }

        /** The fake plugin builds flat rows; this regroups them under their parent as the real envelope nests them. */
        @Override
        public String fetchPage(int start, int limit) {
            plugin.calls.add(new int[]{start, limit});
            int l = limit <= 0 ? 100 : Math.min(limit, 1000);
            int count = Math.max(0, Math.min(plugin.parents, start + l) - start);
            StringBuilder parents = new StringBuilder();
            for (int i = start; i < start + count; i++) {
                StringBuilder nested = new StringBuilder();
                for (int k = 0; k < plugin.rowsOf.applyAsInt(i); k++) {
                    if (nested.length() > 0) nested.append(',');
                    nested.append(plugin.svc.row().build(i, k));
                }
                if (parents.length() > 0) parents.append(',');
                // request ids must be IIQ-shaped (32 hex) for the full-scan import's deterministic identity
                parents.append("{\"sourceId\":\"").append(String.format("%032x", i + 1)).append("\",\"")
                        .append(array).append("\":[")
                        .append(nested).append("]}");
            }
            return "{\"sourceCount\":" + plugin.parents + ",\"limit\":" + l + ",\"rows\":[" + parents + "]}";
        }

        static String envelope(int s, int c, int l, int t, String rows, String run) {
            throw new UnsupportedOperationException("request-shaped services build their envelope in fetchPage");
        }
    }

    private static List<String> keys(Service svc, List<Map<String, Object>> rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(String.valueOf(r.get(svc.outKey()))));
        return out;
    }

    private static final Map<String, String> NO_FILTERS = new LinkedHashMap<>();

    /** Parent 0 expands to two rows, every other parent to one. */
    private static int dense(int i) {
        return i == 0 ? 2 : 1;
    }

    @ParameterizedTest
    @MethodSource("services")
    void limitReadsOneParentPage(Service svc) {
        Plugin plugin = new Plugin(svc, 10_000, KfAgentDerivedPagingTest::dense);
        List<String> rows = keys(svc, svc.fetch().fetch(plugin, NO_FILTERS, null, 10));

        assertEquals(plugin.all().subList(0, 10), rows);
        assertEquals(1, plugin.calls.size(), "one plugin call, not a scan of 10,000 parents");
        assertEquals(0, plugin.calls.get(0)[0]);
        assertEquals(500, plugin.calls.get(0)[1]);
    }

    @ParameterizedTest
    @MethodSource("services")
    void startAndLimitCountResponseRows(Service svc) {
        Plugin plugin = new Plugin(svc, 10_000, KfAgentDerivedPagingTest::dense);
        List<String> rows = keys(svc, svc.fetch().fetch(plugin, NO_FILTERS, 1, 2));

        assertEquals(List.of(key(0, 1), key(1, 0)), rows, "row 1 is parent 0's 2nd row, not parent 1");
        assertEquals(1, plugin.calls.size());
    }

    @ParameterizedTest
    @MethodSource("services")
    void startWithoutLimitReturnsTheRestAndStopsAtTheEnd(Service svc) {
        Plugin plugin = new Plugin(svc, 2_500, i -> 1);
        List<String> rows = keys(svc, svc.fetch().fetch(plugin, NO_FILTERS, 2_000, null));

        assertEquals(plugin.all().subList(2_000, 2_500), rows);
        // rows are counted after expansion, so parents are read from the start: 5 full pages + the empty end page
        assertEquals(6, plugin.calls.size());
    }

    @ParameterizedTest
    @MethodSource("services")
    void filterStopsAtThePageHoldingTheMatch(Service svc) {
        Plugin plugin = new Plugin(svc, 10_000, i -> 1);
        List<String> rows = keys(svc, svc.fetch().fetch(plugin, Map.of(svc.outKey(), key(1_234, 0)), null, 1));

        assertEquals(List.of(key(1_234, 0)), rows);
        assertEquals(3, plugin.calls.size(), "parent pages 0, 500, 1000 only");
    }

    @ParameterizedTest
    @MethodSource("services")
    void sparseRowsWithLimitOneUseFullParentPages(Service svc) {
        Plugin plugin = new Plugin(svc, 5_000, i -> i == 4_990 ? 1 : 0);
        List<String> rows = keys(svc, svc.fetch().fetch(plugin, NO_FILTERS, null, 1));

        assertEquals(List.of(key(4_990, 0)), rows);
        assertEquals(10, plugin.calls.size(), "5,000 parents in pages of 500, not one call per parent");
    }

    @ParameterizedTest
    @MethodSource("services")
    void noParametersAndNegativeLimitReturnEverything(Service svc) {
        Plugin plugin = new Plugin(svc, 1_234, KfAgentDerivedPagingTest::dense);
        assertEquals(plugin.all(), keys(svc, svc.fetch().fetch(plugin, NO_FILTERS, null, null)), "no parameters");
        assertEquals(plugin.all(), keys(svc, svc.fetch().fetch(plugin, NO_FILTERS, null, -1)), "limit=-1");
        assertEquals(plugin.all(), keys(svc, svc.fetch().fetch(plugin, null, null, null)), "null filters");
    }
}
