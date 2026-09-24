package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeTaskResultRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Attributes;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps a native {@code sailpoint.object.TaskResult} into a {@link NativeTaskResultRow}. Read-only:
 * only getters verified against the real 8.4 {@code identityiq.jar}. Nothing is inferred — a native
 * {@code null} stays {@code null}.
 *
 * <p><b>Type/status</b> come from {@code getType()}/{@code getCompletionStatus()} rendered via
 * {@code toString()} (their enum classes are not needed on the wire). <b>Messages</b> are captured by
 * {@code getKey()} + {@code getType()} only — never {@code getLocalizedMessage()}, which transitively
 * references {@code openconnector.OpenMessagePart} and is not on the native compile classpath.
 *
 * <p><b>Credential safety:</b> the statistics attribute map can, in principle, carry a secret; any key
 * whose name matches a password/secret/token/credential pattern is redacted to {@code "<redacted>"}
 * before the value is JSON-safed.
 */
public final class NativeTaskResultMapper {

    private static final String REDACTED = "<redacted>";
    private static final Pattern SECRET_NAME =
            Pattern.compile("(?i).*(password|passwd|secret|token|credential|private[_ -]?key).*");

    private NativeTaskResultMapper() {
    }

    public static NativeTaskResultRow map(TaskResult tr, String sourceSystem, String extractionRunId) {
        NativeTaskResultRow row = new NativeTaskResultRow();

        row.setSourceId(tr.getId());
        row.setName(tr.getName());

        Object type = tr.getType();
        row.setType(type == null ? null : type.toString());
        Object status = tr.getCompletionStatus();
        row.setCompletionStatus(status == null ? null : status.toString());

        TaskDefinition def = tr.getDefinition();
        if (def != null) {
            row.setDefinitionName(def.getName());
        }

        row.setLauncher(tr.getLauncher());
        row.setHost(tr.getHost());
        row.setTargetName(tr.getTargetName());
        row.setTargetClass(tr.getTargetClass());
        row.setTargetId(tr.getTargetId());
        row.setSchedule(tr.getSchedule());
        row.setProgress(tr.getProgress());

        row.setPercentComplete(Integer.valueOf(tr.getPercentComplete()));
        row.setRunLength(Integer.valueOf(tr.getRunLength()));
        row.setPendingSignoffs(Integer.valueOf(tr.getPendingSignoffs()));

        row.setPartitioned(Boolean.valueOf(tr.isPartitioned()));
        row.setTerminateRequested(Boolean.valueOf(tr.isTerminateRequested()));
        row.setComplete(Boolean.valueOf(tr.isComplete()));

        row.setLaunched(toInstant(tr.getLaunched()));
        row.setCompleted(toInstant(tr.getCompleted()));
        row.setExpiration(toInstant(tr.getExpiration()));
        row.setVerified(toInstant(tr.getVerified()));

        // Messages are read reflectively (getKey/getType only) so the compiler never has to resolve
        // sailpoint.tools.Message's transitive openconnector.OpenMessagePart reference, and getLocalizedMessage()
        // is never touched. The declared List<?> keeps the Message type out of this method's signature.
        addMessages(tr.getMessages(), row.getMessages());

        Attributes<String, Object> attrs = tr.getAttributes();
        if (attrs != null) {
            for (Object key : attrs.keySet()) {
                if (key != null) {
                    String k = key.toString();
                    row.getAttributes().put(k,
                            SECRET_NAME.matcher(k).matches() ? REDACTED : JsonSafe.toJsonSafe(attrs.get(key)));
                }
            }
        }

        row.setCreated(toInstant(tr.getCreated()));
        row.setModified(toInstant(tr.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    /** Reflective {@code getKey()}/{@code getType()} read of each {@code sailpoint.tools.Message} (raw list). */
    private static void addMessages(List<?> messages, List<Map<String, Object>> out) {
        if (messages == null) {
            return;
        }
        for (Object m : messages) {
            if (m == null) {
                continue;
            }
            Map<String, Object> mm = new LinkedHashMap<String, Object>();
            mm.put("key", reflect(m, "getKey"));
            mm.put("type", reflect(m, "getType"));
            out.add(mm);
        }
    }

    /** Invokes a no-arg getter reflectively and returns its {@code toString()} (or null); never throws. */
    private static String reflect(Object target, String getter) {
        try {
            Object v = target.getClass().getMethod(getter).invoke(target);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
