package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeTaskScheduleRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.TaskSchedule;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps a native {@code sailpoint.object.TaskSchedule} into a {@link NativeTaskScheduleRow}. Read-only:
 * only getters verified against the real 8.4 {@code identityiq.jar}. Nothing is inferred — a native
 * {@code null} stays {@code null}. {@code getState()}/{@code getNewState()} are rendered via
 * {@code toString()} (their enum class is not needed on the wire).
 *
 * <p><b>Credential safety:</b> a schedule's argument map can carry a secret; any key whose name matches
 * a password/secret/token/credential pattern is redacted to {@code "<redacted>"} before JSON-safing.
 */
public final class NativeTaskScheduleMapper {

    private static final String REDACTED = "<redacted>";
    private static final Pattern SECRET_NAME =
            Pattern.compile("(?i).*(password|passwd|secret|token|credential|private[_ -]?key).*");

    private NativeTaskScheduleMapper() {
    }

    public static NativeTaskScheduleRow map(TaskSchedule ts, String sourceSystem, String extractionRunId) {
        NativeTaskScheduleRow row = new NativeTaskScheduleRow();

        row.setSourceId(ts.getId());
        row.setName(ts.getName());
        row.setDescription(ts.getDescription());
        row.setDefinitionName(ts.getDefinitionName());

        Object state = ts.getState();
        row.setState(state == null ? null : state.toString());
        Object newState = ts.getNewState();
        row.setNewState(newState == null ? null : newState.toString());

        row.setLauncher(ts.getLauncher());
        row.setHost(ts.getHost());
        row.setLastLaunchError(ts.getLastLaunchError());
        row.setDeleteOnFinish(Boolean.valueOf(ts.isDeleteOnFinish()));

        row.setLastExecution(toInstant(ts.getLastExecution()));
        row.setNextExecution(toInstant(ts.getNextExecution()));
        row.setNextActualExecution(toInstant(ts.getNextActualExecution()));
        row.setResumeDate(toInstant(ts.getResumeDate()));

        List<String> crons = ts.getCronExpressions();
        if (crons != null) {
            for (String c : crons) {
                if (c != null) {
                    row.getCronExpressions().add(c);
                }
            }
        }

        Map<String, Object> args = ts.getArguments();
        if (args != null) {
            for (Map.Entry<String, Object> e : args.entrySet()) {
                if (e.getKey() != null) {
                    String k = e.getKey();
                    row.getArguments().put(k,
                            SECRET_NAME.matcher(k).matches() ? REDACTED : JsonSafe.toJsonSafe(e.getValue()));
                }
            }
        }

        row.setCreated(toInstant(ts.getCreated()));
        row.setModified(toInstant(ts.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
