package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativePolicyConstraintMapper;
import com.keyforge.nativeiiq.model.NativePolicyConstraintExtractionResult;

import sailpoint.api.SailPointContext;
import sailpoint.object.Policy;
import sailpoint.object.BaseConstraint;
import sailpoint.object.SODConstraint;
import sailpoint.object.GenericConstraint;
import sailpoint.object.ActivityConstraint;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Native source adapter for Policy constraints. Pages over the parent {@code Policy} (the unit the
 * constraints are reached through): projects Policy ids, loads each, expands its constraints, decaches.
 * Emits the total Policy count ({@code countObjects}) + the number of policies this page covered so the
 * client can guard a complete scan. STRICTLY READ-ONLY.
 */
public final class NativePolicyConstraintExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativePolicyConstraintExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativePolicyConstraintExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    public NativePolicyConstraintExtractionResult extract(int start, int limit, Date modifiedAfter) throws GeneralException {
        Set<String> childChangedPolicyIds = changedConstraintParents(modifiedAfter);
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        addIncrementalFilter(qo, modifiedAfter, childChangedPolicyIds);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }

        NativePolicyConstraintExtractionResult result = new NativePolicyConstraintExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "PolicyConstraint", Instant.now());
        QueryOptions countOptions = new QueryOptions();
        addIncrementalFilter(countOptions, modifiedAfter, childChangedPolicyIds);
        result.setSourceCount(context.countObjects(Policy.class, countOptions));

        int policies = 0;
        Iterator<Object[]> ids = context.search(Policy.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Policy p = context.getObjectById(Policy.class, id);
            if (p == null) {
                continue;
            }
            try {
                policies++;
                NativePolicyConstraintMapper.mapInto(p, result.getConstraints(),
                        config.getSourceSystem(), config.getExtractionRunId());
            } finally {
                context.decache(p);
            }
        }
        result.setPolicyCount(policies);
        result.setFinishedAt(Instant.now());
        return result;
    }

    private Set<String> changedConstraintParents(Date modifiedAfter) throws GeneralException {
        Set<String> ids = new LinkedHashSet<String>();
        if (modifiedAfter == null) return ids;
        collectChangedConstraintParents(SODConstraint.class, modifiedAfter, ids);
        collectChangedConstraintParents(ActivityConstraint.class, modifiedAfter, ids);
        collectChangedConstraintParents(GenericConstraint.class, modifiedAfter, ids);
        return ids;
    }

    private <T extends BaseConstraint> void collectChangedConstraintParents(Class<T> type, Date modifiedAfter,
                                                                             Set<String> policyIds)
            throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyModifiedAfter(qo, modifiedAfter);
        Iterator<T> changed = context.search(type, qo);
        while (changed != null && changed.hasNext()) {
            T constraint = changed.next();
            if (constraint == null) continue;
            try {
                Policy policy = constraint.getPolicy();
                if (policy != null && policy.getId() != null) policyIds.add(policy.getId());
            } finally {
                context.decache(constraint);
            }
        }
    }

    private static void addIncrementalFilter(QueryOptions options, Date modifiedAfter, Set<String> childChangedPolicyIds) {
        if (modifiedAfter == null) return;
        Filter parentChanged = NativeIncrementalQuery.changedAfterFilter(modifiedAfter);
        if (childChangedPolicyIds == null || childChangedPolicyIds.isEmpty()) options.addFilter(parentChanged);
        else options.addFilter(Filter.or(parentChanged, Filter.in("id", childChangedPolicyIds)));
    }
}
