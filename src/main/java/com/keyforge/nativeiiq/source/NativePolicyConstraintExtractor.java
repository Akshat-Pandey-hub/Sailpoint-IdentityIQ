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
        return extract(start, limit, modifiedAfter, true);
    }

    /**
     * One page; the population is counted only when {@code includeCount} is true. Counting reads the whole
     * (bounded) population, so a caller that only needs the page must not ask for it; the result then reports
     * {@code -1} (not counted).
     */
    public NativePolicyConstraintExtractionResult extract(int start, int limit, Date modifiedAfter,
            boolean includeCount) throws GeneralException {
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
        if (includeCount) {
            result.setSourceCount(context.countObjects(Policy.class, countOptions));
        }

        int policies = 0;
        Iterator<Object[]> ids = context.search(Policy.class, qo, "id");
        try {
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
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }
        result.setPolicyCount(policies);
        result.setFinishedAt(Instant.now());
        return result;
    }

    private Set<String> changedConstraintParents(Date modifiedAfter) throws GeneralException {
        Set<String> ids = new LinkedHashSet<String>();
        if (modifiedAfter == null) return ids;
        if (!collectChangedConstraintParents(SODConstraint.class, modifiedAfter, ids)) return null;
        if (!collectChangedConstraintParents(ActivityConstraint.class, modifiedAfter, ids)) return null;
        if (!collectChangedConstraintParents(GenericConstraint.class, modifiedAfter, ids)) return null;
        return ids;
    }

    private <T extends BaseConstraint> boolean collectChangedConstraintParents(Class<T> type, Date modifiedAfter,
                                                                                Set<String> policyIds)
            throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyModifiedAfter(qo, modifiedAfter);
        Iterator<T> changed = context.search(type, qo);
        try {
            while (changed != null && changed.hasNext()) {
                T constraint = changed.next();
                if (constraint == null) continue;
                try {
                    Policy policy = constraint.getPolicy();
                    if (policy != null && policy.getId() != null) {
                        policyIds.add(policy.getId());
                        if (NativeIncrementalQuery.exceedsChangedParentIdLimit(policyIds)) return false;
                    }
                } finally {
                    context.decache(constraint);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(changed);
        }
        return true;
    }

    private static void addIncrementalFilter(QueryOptions options, Date modifiedAfter, Set<String> childChangedPolicyIds) {
        if (modifiedAfter == null) return;
        if (childChangedPolicyIds == null) return; // too many changed children: safely fall back to all parents
        Filter parentChanged = NativeIncrementalQuery.changedAfterFilter(modifiedAfter);
        if (childChangedPolicyIds == null || childChangedPolicyIds.isEmpty()) options.addFilter(parentChanged);
        else options.addFilter(Filter.or(parentChanged, Filter.in("id", childChangedPolicyIds)));
    }

}
