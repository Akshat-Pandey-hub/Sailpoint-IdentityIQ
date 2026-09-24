package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativePolicyConstraintRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.ActivityConstraint;
import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.GenericConstraint;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Policy;
import sailpoint.object.SODConstraint;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Expands a {@code Policy} into its constraint rows via the supported typed getters
 * ({@code getSODConstraints()} / {@code getGenericConstraints()} / {@code getActivityConstraints()}).
 * Read-only; only getters verified against the 8.4 {@code identityiq.jar}. Explicit references only: the
 * parent {@code policyId}, and for SoD the conflicting {@code Bundle} ids on each side. Nothing inferred.
 */
public final class NativePolicyConstraintMapper {

    public static final String TYPE_SOD = "SOD";
    public static final String TYPE_GENERIC = "GENERIC";
    public static final String TYPE_ACTIVITY = "ACTIVITY";

    private static final String REDACTED = "<redacted>";
    private static final Pattern SECRET_NAME =
            Pattern.compile("(?i).*(password|passwd|secret|token|credential|private[_ -]?key).*");

    private NativePolicyConstraintMapper() {
    }

    /** Appends every constraint of {@code policy} to {@code out}. Returns the number appended. */
    public static int mapInto(Policy policy, List<NativePolicyConstraintRow> out, String system, String runId) {
        String policyId = policy.getId();
        String policyName = policy.getName();
        int count = 0;

        List<SODConstraint> sod = policy.getSODConstraints();
        if (sod != null) {
            for (SODConstraint c : sod) {
                if (c == null) {
                    continue;
                }
                NativePolicyConstraintRow row = base(c, TYPE_SOD, policyId, policyName, system, runId);
                addBundles(c.getLeftBundles(), row.getLeftBundles());
                addBundles(c.getRightBundles(), row.getRightBundles());
                out.add(row);
                count++;
            }
        }

        List<GenericConstraint> generic = policy.getGenericConstraints();
        if (generic != null) {
            for (GenericConstraint c : generic) {
                if (c == null) {
                    continue;
                }
                NativePolicyConstraintRow row = base(c, TYPE_GENERIC, policyId, policyName, system, runId);
                List<IdentitySelector> selectors = c.getSelectors();
                if (selectors != null) {
                    row.setSelectorCount(Integer.valueOf(selectors.size()));
                    for (IdentitySelector s : selectors) {
                        if (s != null) {
                            row.getSelectors().add(JsonSafe.toJsonSafe(s));
                        }
                    }
                }
                out.add(row);
                count++;
            }
        }

        List<ActivityConstraint> activity = policy.getActivityConstraints();
        if (activity != null) {
            for (ActivityConstraint c : activity) {
                if (c == null) {
                    continue;
                }
                out.add(base(c, TYPE_ACTIVITY, policyId, policyName, system, runId));
                count++;
            }
        }
        return count;
    }

    private static NativePolicyConstraintRow base(BaseConstraint c, String type, String policyId,
                                                  String policyName, String system, String runId) {
        NativePolicyConstraintRow row = new NativePolicyConstraintRow();
        row.setSourceId(c.getId());
        row.setPolicyId(policyId);
        row.setPolicyName(policyName);
        row.setName(c.getName());
        row.setDescription(c.getDescription());
        row.setConstraintType(type);
        row.setWeight(Integer.valueOf(c.getWeight()));
        row.setCompensatingControl(c.getCompensatingControl());

        Identity owner = c.getViolationOwner();
        if (owner != null) {
            row.setViolationOwnerId(owner.getId());
            row.setViolationOwnerName(owner.getName());
        }
        Object vot = c.getViolationOwnerType();
        row.setViolationOwnerType(vot == null ? null : vot.toString());

        Attributes<String, Object> args = c.getArguments();
        if (args != null) {
            for (Object key : args.keySet()) {
                if (key != null) {
                    String k = key.toString();
                    row.getArguments().put(k,
                            SECRET_NAME.matcher(k).matches() ? REDACTED : JsonSafe.toJsonSafe(args.get(key)));
                }
            }
        }

        row.setCreated(toInstant(c.getCreated()));
        row.setModified(toInstant(c.getModified()));
        row.setSrcSystem(system);
        row.setExtractionRunId(runId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static void addBundles(List<Bundle> bundles, List<Map<String, Object>> out) {
        if (bundles == null) {
            return;
        }
        for (Bundle b : bundles) {
            if (b != null) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", b.getId());
                m.put("name", b.getName());
                out.add(m);
            }
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
