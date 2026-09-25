package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativePolicyRow;
import com.keyforge.nativeiiq.wire.NativeSerialize;

import sailpoint.object.Identity;
import sailpoint.object.Policy;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Maps a native {@code sailpoint.object.Policy} into a {@link NativePolicyRow}. Read-only; only getters
 * verified against the 8.4 {@code identityiq.jar}. Nothing is inferred. The constraint list is summarized
 * by count (the individual constraints are policy-type-specific and not materialized this stage).
 */
public final class NativePolicyMapper {

    private NativePolicyMapper() {
    }

    public static NativePolicyRow map(Policy p, String sourceSystem, String extractionRunId) {
        NativePolicyRow row = new NativePolicyRow();

        row.setSourceId(p.getId());
        row.setName(p.getName());
        row.setType(p.getType());
        row.setTypeKey(p.getTypeKey());
        row.setDescription(p.getDescription());
        Map<String, String> descriptions = p.getDescriptions();
        if (descriptions != null) {
            for (Map.Entry<String, String> e : descriptions.entrySet()) {
                if (e.getKey() != null) {
                    row.getDescriptions().put(e.getKey(), e.getValue());
                }
            }
        }
        row.setExecutor(p.getExecutor());

        Identity owner = p.getViolationOwner();
        if (owner != null) {
            row.setViolationOwnerId(owner.getId());
            row.setViolationOwnerName(owner.getName());
        }

        List<?> constraints = p.getConstraints();
        row.setConstraintCount(Integer.valueOf(constraints == null ? 0 : constraints.size()));

        // Additional native governance fields (source-truth; complex objects serialized to IIQ XML).
        row.setState(NativeSerialize.enumName(p.getState()));
        row.setViolationRule(p.getViolationRule());
        row.setViolationWorkflow(p.getViolationWorkflow());
        row.setSignature(NativeSerialize.xml(p.getSignature()));
        row.setCertificationActions(p.getCertificationActions());

        row.setCreated(toInstant(p.getCreated()));
        row.setModified(toInstant(p.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
