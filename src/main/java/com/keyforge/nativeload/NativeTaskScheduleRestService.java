package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KF Agent REST read service for native task schedules ({@code sailpoint.object.TaskSchedule} →
 * {@code kf_task_schedule}). Reads the {@link NativeTaskScheduleClient} page source and parses it with
 * {@link NativeTaskScheduleParser}; nothing is persisted and no PostgreSQL is touched on this path.
 *
 * <p><b>Paging.</b> IdentityIQ does not page TaskSchedule: it ignores {@code firstRow}/{@code resultLimit} for this
 * object (verified live), so the plugin now filters and pages schedules in code; older plugins returned every schedule
 * on every call. Either way the agent reads all schedules once and applies the filters and the {@code start}/{@code limit} window itself. A system
 * has only a small, fixed set of schedules (one per scheduled task), so this read is always small.
 *
 * <p><b>REST contract</b> (verified from {@link NativeTaskScheduleRecord} + {@link NativeTaskScheduleParser} +
 * {@code kf_task_schedule}): 18 SailPoint-facing fields. {@code cron_expressions} and {@code arguments} are
 * the structured ({@code jsonb}) fields — returned as JSON, not filterable. {@code delete_on_finish} is a
 * boolean; {@code last_execution_at}/{@code next_execution_at}/{@code next_actual_execution_at}/
 * {@code resume_at}/{@code created_at}/{@code modified_at} are ISO-8601 timestamps; every other field is text
 * ({@code source_id} is the schedule's name — TaskSchedule has no GUID). The 16 scalar fields are filterable.
 * The KeyForge {@code taskscheduleid} PK, {@code record_hash}, lineage envelope and soft-delete columns are
 * excluded. The agent passes {@code modifiedAfter} through to the plugin, which filters loaded schedules in code because IIQ ignores query filters;
 * IIQ stores no created/modified on schedules (null live), and such undated schedules are always returned.
 */
public final class NativeTaskScheduleRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final NativeTaskScheduleParser parser = new NativeTaskScheduleParser();

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The two jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativeTaskScheduleRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * All schedules (see the class note on paging) → filter → {@code start}/{@code limit} window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeTaskSchedulePageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeTaskScheduleRecord> matched = new ArrayList<>();
        for (NativeTaskScheduleRecord r : readAllSchedules(source)) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : Math.min(start, matched.size());
        int to = (limit == null || limit < 0) ? matched.size() : (int) Math.min(matched.size(), (long) from + limit);

        List<Map<String, Object>> out = new ArrayList<>(Math.max(0, to - from));
        for (NativeTaskScheduleRecord r : matched.subList(from, Math.max(from, to))) {
            out.add(toJson(r));
        }
        return out;
    }

    /**
     * Every schedule, each once, in the plugin's (name) order. Pages are read until one is short or adds no schedule
     * not already seen (by {@code source_id}, the schedule name): IIQ returns the whole set on every call, so the
     * second call adds nothing and reading stops — and if a future IIQ does honour paging, this still reads every page.
     */
    private List<NativeTaskScheduleRecord> readAllSchedules(NativeTaskSchedulePageSource source) {
        Map<String, NativeTaskScheduleRecord> byId = new LinkedHashMap<>();
        int start = 0;
        while (true) {
            List<NativeTaskScheduleRecord> page = parser.parse(source.fetchPage(start, INTERNAL_PAGE_SIZE));
            int added = 0;
            for (NativeTaskScheduleRecord r : page) {
                // TaskSchedule names are required and unique in IIQ; source_id is the name
                String key = String.valueOf(r.sourceId != null ? r.sourceId : r.name);
                if (byId.putIfAbsent(key, r) == null) {
                    added++;
                }
            }
            if (page.size() < INTERNAL_PAGE_SIZE || added == 0) {
                return new ArrayList<>(byId.values());
            }
            start += page.size();
        }
    }

    private static boolean matches(NativeTaskScheduleRecord r, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> f : filters.entrySet()) {
            String actual = SCALARS.get(f.getKey()).apply(r);
            if (actual == null || !actual.equals(f.getValue())) {
                return false;
            }
        }
        return true;
    }


    /** The SailPoint-facing fields, keyed by our DB column names (kf_task_schedule order). */
    private Map<String, Object> toJson(NativeTaskScheduleRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("description", r.description);
        m.put("definition_name", r.definitionName);
        m.put("state", r.state);
        m.put("new_state", r.newState);
        m.put("launcher", r.launcher);
        m.put("host", r.host);
        m.put("last_launch_error", r.lastLaunchError);
        m.put("delete_on_finish", r.deleteOnFinish);              // boolean
        m.put("last_execution_at", iso(r.lastExecution));
        m.put("next_execution_at", iso(r.nextExecution));
        m.put("next_actual_execution_at", iso(r.nextActualExecution));
        m.put("resume_at", iso(r.resumeDate));
        m.put("cron_expressions", node(r.cronExpressionsJson));   // jsonb
        m.put("arguments", node(r.argumentsJson));                // jsonb
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeTaskScheduleRecord, String>> buildScalars() {
        Map<String, Function<NativeTaskScheduleRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("description", r -> r.description);
        m.put("definition_name", r -> r.definitionName);
        m.put("state", r -> r.state);
        m.put("new_state", r -> r.newState);
        m.put("launcher", r -> r.launcher);
        m.put("host", r -> r.host);
        m.put("last_launch_error", r -> r.lastLaunchError);
        m.put("delete_on_finish", r -> r.deleteOnFinish == null ? null : r.deleteOnFinish.toString());
        m.put("last_execution_at", r -> iso(r.lastExecution));
        m.put("next_execution_at", r -> iso(r.nextExecution));
        m.put("next_actual_execution_at", r -> iso(r.nextActualExecution));
        m.put("resume_at", r -> iso(r.resumeDate));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        return m;
    }

    /** Re-hydrate a pre-serialized jsonb string into a JSON value/array/object; null/empty stays null. */
    private Object node(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(json);
            return n == null || n.isNull() ? null : n;
        } catch (Exception e) {
            return json;
        }
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
