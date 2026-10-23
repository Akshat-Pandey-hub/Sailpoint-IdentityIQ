package com.keyforge.nativeload;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Upstream reads of the KF Agent services that page their plugin source 1:1. The fake plugin behaves like the
 * KeyForge plugin resources ({@code firstRow=start}, {@code resultLimit=min(limit, 1000)}, fixed order, echoes the
 * effective {@code limit}) and records every call, so each test proves how much of the population a request
 * actually read from IIQ, not only how many rows it returned.
 */
class KfAgentServicePagingTest {

    /** Plugin-like fake over {@code size} rows {@code {"sourceId":"id00000"}..}, recording every (start, limit). */
    static final class Plugin {
        final String entity;
        final int size;
        final List<int[]> calls = new ArrayList<>();
        int countChangesAfterCall = -1; // when >= 0, sourceCount reported after that call index is size + 1

        Plugin(String entity, int size) {
            this.entity = entity;
            this.size = size;
        }

        String fetchPage(int start, int limit) {
            calls.add(new int[]{start, limit});
            int effective = limit <= 0 ? 100 : Math.min(limit, 1000);
            StringBuilder rows = new StringBuilder();
            for (int i = start; i < Math.min(size, start + effective); i++) {
                if (rows.length() > 0) {
                    rows.append(',');
                }
                rows.append("{\"sourceId\":\"").append(id(i)).append("\"}");
            }
            int count = countChangesAfterCall >= 0 && calls.size() - 1 > countChangesAfterCall ? size + 1 : size;
            return "{\"entity\":\"" + entity + "\",\"limit\":" + effective + ",\"sourceCount\":" + count
                    + ",\"rows\":[" + rows + "]}";
        }

        int rowsRead() {
            int n = 0;
            for (int[] c : calls) {
                n += Math.max(0, Math.min(size, c[0] + Math.min(c[1], 1000)) - c[0]);
            }
            return n;
        }
    }

    /** One service under test: its name and how to call its fetch with the fake plugin as the page source. */
    interface Fetch {
        List<Map<String, Object>> fetch(Plugin plugin, Map<String, String> filters, Integer start, Integer limit);
    }

    record Service(String name, String entity, Fetch fetch) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Service> services() {
        return Stream.of(
                new Service("identities", "Identity",
                        (p, f, s, l) -> new NativeIdentityRestService().fetch(p::fetchPage, f, s, l)),
                new Service("accounts", "Link",
                        (p, f, s, l) -> new NativeAccountRestService().fetch(p::fetchPage, f, s, l)),
                new Service("applications", "Application",
                        (p, f, s, l) -> new NativeApplicationRestService().fetch(p::fetchPage, f, s, l)),
                new Service("roles", "Bundle",
                        (p, f, s, l) -> new NativeRoleRestService().fetch(p::fetchPage, f, s, l)),
                new Service("workgroups", "Workgroup",
                        (p, f, s, l) -> new NativeWorkgroupRestService().fetch(p::fetchPage, f, s, l)),
                new Service("policies", "Policy",
                        (p, f, s, l) -> new NativePolicyRestService().fetch(p::fetchPage, f, s, l)),
                new Service("policy-violations", "PolicyViolation",
                        (p, f, s, l) -> new NativeViolationRestService().fetch(p::fetchPage, f, s, l)),
                new Service("task-results", "TaskResult",
                        (p, f, s, l) -> new NativeTaskResultRestService().fetch(p::fetchPage, f, s, l)),
                new Service("task-schedules", "TaskSchedule",
                        (p, f, s, l) -> new NativeTaskScheduleRestService().fetch(p::fetchPage, f, s, l)),
                new Service("group-definitions", "GroupDefinition",
                        (p, f, s, l) -> new NativeGroupDefinitionRestService().fetch(p::fetchPage, f, s, l)),
                new Service("audit-events", "AuditEvent",
                        (p, f, s, l) -> new NativeAuditEventRestService().fetch(p::fetchPage, f, s, l)),
                new Service("workitem-archives", "WorkItemArchive",
                        (p, f, s, l) -> new NativeWorkItemArchiveRestService().fetch(p::fetchPage, f, s, l)),
                new Service("certification-archives", "CertificationArchive",
                        (p, f, s, l) -> new NativeCertificationArchiveRestService().fetch(p::fetchPage, f, s, l)),
                new Service("workflow-definitions", "Workflow",
                        (p, f, s, l) -> new NativeWorkflowDefinitionRestService().fetch(p::fetchPage, f, s, l)));
    }

    /** Services whose import refuses a source count that changes mid-scan; their bounded reads must too. */
    static Stream<Service> countGuardedServices() {
        return services().filter(s -> List.of("audit-events", "workitem-archives", "certification-archives")
                .contains(s.name()));
    }

    static String id(int i) {
        return String.format("id%05d", i);
    }

    private static List<Object> ids(List<Map<String, Object>> rows) {
        List<Object> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.get("source_id")));
        return out;
    }

    @ParameterizedTest
    @MethodSource("services")
    void limitReadsOnlyThatWindow(Service svc) {
        Plugin plugin = new Plugin(svc.entity(), 10_000);
        List<Map<String, Object>> rows = svc.fetch().fetch(plugin, null, null, 10);

        assertEquals(List.of(id(0), id(1), id(2), id(3), id(4), id(5), id(6), id(7), id(8), id(9)), ids(rows));
        assertEquals(1, plugin.calls.size(), "one plugin call, not a scan of 10,000");
        assertEquals(0, plugin.calls.get(0)[0]);
        assertEquals(10, plugin.calls.get(0)[1]);
    }

    @ParameterizedTest
    @MethodSource("services")
    void startAndLimitArePassedToThePlugin(Service svc) {
        Plugin plugin = new Plugin(svc.entity(), 10_000);
        List<Map<String, Object>> rows = svc.fetch().fetch(plugin, null, 1, 2);

        assertEquals(List.of(id(1), id(2)), ids(rows));
        assertEquals(1, plugin.calls.size());
        assertEquals(1, plugin.calls.get(0)[0], "plugin start=1");
        assertEquals(2, plugin.calls.get(0)[1], "plugin limit=2");
    }

    @ParameterizedTest
    @MethodSource("services")
    void startWithoutLimitNeverReadsTheSkippedRows(Service svc) {
        Plugin plugin = new Plugin(svc.entity(), 2_500);
        List<Map<String, Object>> rows = svc.fetch().fetch(plugin, null, 2_000, null);

        assertEquals(500, rows.size());
        assertEquals(id(2_000), rows.get(0).get("source_id"));
        assertEquals(2_000, plugin.calls.get(0)[0], "first plugin call starts at the offset");
        assertEquals(500, plugin.rowsRead());
    }

    @ParameterizedTest
    @MethodSource("services")
    void filterStopsOnceTheWindowIsFilled(Service svc) {
        Plugin plugin = new Plugin(svc.entity(), 10_000);
        List<Map<String, Object>> rows = svc.fetch().fetch(plugin, Map.of("source_id", id(1_234)), null, 1);

        assertEquals(List.of(id(1_234)), ids(rows));
        assertEquals(3, plugin.calls.size(), "pages 0, 500, 1000 only");
        assertEquals(1_500, plugin.rowsRead());
    }

    @ParameterizedTest
    @MethodSource("services")
    void noParametersStillReturnsEverything(Service svc) {
        Plugin plugin = new Plugin(svc.entity(), 1_234);
        assertEquals(1_234, svc.fetch().fetch(plugin, null, null, null).size());
        assertEquals(1_234, plugin.rowsRead());
    }

    @ParameterizedTest
    @MethodSource("countGuardedServices")
    void sourceCountChangingBetweenPagesFailsInsteadOfReturningShiftedRows(Service svc) {
        Plugin plugin = new Plugin(svc.entity(), 3_000);
        plugin.countChangesAfterCall = 0;
        assertThrows(NativeImportException.class, () -> svc.fetch().fetch(plugin, null, 0, 2_500));
    }
}
