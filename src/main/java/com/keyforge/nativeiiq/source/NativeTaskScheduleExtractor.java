package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeTaskScheduleMapper;
import com.keyforge.nativeiiq.model.NativeTaskScheduleExtractionResult;
import com.keyforge.nativeiiq.model.NativeTaskScheduleRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Native source adapter for {@code sailpoint.object.TaskSchedule} (task cron schedules). Same flow as
 * the validated native extractors (project ids, load one at a time, map, decache). STRICTLY READ-ONLY:
 * only {@code search}/{@code getObjectById}/{@code decache}. IIQ ignores ordering for TaskSchedule, so the
 * rows are ordered here by name, then id, before the page is taken.
 */
public final class NativeTaskScheduleExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeTaskScheduleExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeTaskScheduleExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * IIQ ignores firstRow/resultLimit (and so cannot filter before paging) for TaskSchedule, so the
     * changed-after filter and the start/limit window are both applied here in code, filter first.
     */
    public NativeTaskScheduleExtractionResult extract(int start, int limit, Date modifiedAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("name", true);
        qo.addOrdering("id", true);
        return extract(qo, Math.max(0, start), limit, modifiedAfter);
    }

    private NativeTaskScheduleExtractionResult extract(QueryOptions qo, int start, int limit, Date modifiedAfter)
            throws GeneralException {
        NativeTaskScheduleExtractionResult result = new NativeTaskScheduleExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "TaskSchedule", Instant.now());

        // IIQ ignores the requested ordering (and paging) for TaskSchedule, so the order it returns is not
        // guaranteed between calls. Read every schedule (there are only a handful), keep the changed ones, and
        // fix the order here before the start/limit window is taken, so pages never repeat or skip a schedule.
        List<NativeTaskScheduleRow> matched = new ArrayList<NativeTaskScheduleRow>();
        Iterator<Object[]> ids = context.search(TaskSchedule.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                TaskSchedule ts = context.getObjectById(TaskSchedule.class, id);
                if (ts == null) {
                    continue;
                }
                try {
                    // Live TaskSchedule objects have neither timestamp. Keep undated schedules conservatively so
                    // an unavailable timestamp can never make an actually changed schedule disappear.
                    boolean undated = ts.getModified() == null && ts.getCreated() == null;
                    if (!undated && !NativeIncrementalQuery.changedAfter(ts.getModified(), ts.getCreated(), modifiedAfter)) {
                        continue;
                    }
                    matched.add(NativeTaskScheduleMapper.map(ts, config.getSourceSystem(), config.getExtractionRunId()));
                } finally {
                    context.decache(ts);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }

        result.getTaskSchedules().addAll(window(matched, start, limit));
        result.setFinishedAt(Instant.now());
        return result;
    }

    /** By name, then id (missing values last), compared as plain text so the order is the same on every IIQ. */
    static final Comparator<NativeTaskScheduleRow> ORDER = new Comparator<NativeTaskScheduleRow>() {
        @Override
        public int compare(NativeTaskScheduleRow a, NativeTaskScheduleRow b) {
            int byName = compareText(a.getName(), b.getName());
            return byName != 0 ? byName : compareText(a.getSourceId(), b.getSourceId());
        }
    };

    /** Sorts {@code rows} into {@link #ORDER} and returns the {@code [start, start + limit)} window ({@code limit <= 0}: all). */
    static List<NativeTaskScheduleRow> window(List<NativeTaskScheduleRow> rows, int start, int limit) {
        List<NativeTaskScheduleRow> sorted = new ArrayList<NativeTaskScheduleRow>(rows);
        Collections.sort(sorted, ORDER);
        int from = Math.min(Math.max(0, start), sorted.size());
        int to = limit > 0 ? (int) Math.min((long) sorted.size(), (long) from + limit) : sorted.size();
        return new ArrayList<NativeTaskScheduleRow>(sorted.subList(from, to));
    }

    private static int compareText(String a, String b) {
        if (a == null) {
            return b == null ? 0 : 1;
        }
        return b == null ? -1 : a.compareTo(b);
    }
}
