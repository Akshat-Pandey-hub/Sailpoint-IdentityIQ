package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeViolationRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Maps a native {@code sailpoint.object.PolicyViolation} into a {@link NativeViolationRow}. Read-only;
 * only getters verified against the 8.4 {@code identityiq.jar}. The identity, policy and constraint are
 * captured by their explicit source ids/names ({@code getIdentity()}, {@code getPolicyId()},
 * {@code getConstraintId()}) — nothing is inferred. List/attribute values are made JSON-safe; the
 * argument map is name-pattern redacted for secrets.
 */
public final class NativeViolationMapper {

    private static final String REDACTED = "<redacted>";
    private static final Pattern SECRET_NAME =
            Pattern.compile("(?i).*(password|passwd|secret|token|credential|private[_ -]?key).*");

    private NativeViolationMapper() {
    }

    public static NativeViolationRow map(PolicyViolation v, String sourceSystem, String extractionRunId) {
        NativeViolationRow row = new NativeViolationRow();

        row.setSourceId(v.getId());
        row.setName(v.getName());

        Identity identity = v.getIdentity();
        if (identity != null) {
            row.setIdentityId(identity.getId());
            row.setIdentityName(identity.getName());
        }
        row.setPolicyId(v.getPolicyId());
        row.setPolicyName(v.getPolicyName());
        row.setConstraintId(v.getConstraintId());
        row.setConstraintName(v.getConstraintName());

        Object status = v.getStatus();
        row.setStatus(status == null ? null : status.toString());
        row.setActive(Boolean.valueOf(v.isActive()));

        row.setLeftBundles(v.getLeftBundles());
        row.setRightBundles(v.getRightBundles());
        row.setEntitlementsMarkedForRemediation(v.getEntitlementsMarkedForRemediation());
        row.setBundlesMarkedForRemediation(v.getBundlesMarkedForRemediation());

        List<String> apps = v.getRelevantApps();
        if (apps != null) {
            for (String a : apps) {
                if (a != null) {
                    row.getRelevantApps().add(a);
                }
            }
        }
        List<?> terms = v.getViolatingEntitlements();
        if (terms != null) {
            for (Object t : terms) {
                if (t != null) {
                    row.getViolatingEntitlements().add(JsonSafe.toJsonSafe(t));
                }
            }
        }
        Attributes<String, Object> args = v.getArguments();
        if (args != null) {
            for (Object key : args.keySet()) {
                if (key != null) {
                    String k = key.toString();
                    row.getArguments().put(k,
                            SECRET_NAME.matcher(k).matches() ? REDACTED : JsonSafe.toJsonSafe(args.get(key)));
                }
            }
        }

        row.setCreated(toInstant(v.getCreated()));
        row.setModified(toInstant(v.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
