package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeRoleRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import com.keyforge.nativeiiq.wire.NativeSerialize;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Rule;
import sailpoint.object.Script;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a native {@code sailpoint.object.Bundle} (role) into a {@link NativeRoleRow}. Read-only: only
 * getters. Every access is null-guarded. Uses only getters verified against the real 8.4
 * {@code identityiq.jar}. Nothing is inferred — a native {@code null} stays {@code null}.
 *
 * <p><b>Scope (Stage 5):</b> the role entity itself. Relationship structures Bundle also exposes —
 * inheritance / permits / requirements / profiles / applications — are deliberately NOT mapped here;
 * they are separate upcoming stages. {@code getSelector()} IS extracted here — both the presence flag
 * ({@code has_selector}) and the full assignment/birthright expression (summary, filter, match
 * expression, population, rule, script). Extended attributes are made JSON-safe while the object is
 * still attached to its session (before the extractor decaches).
 */
public final class NativeRoleMapper {

    private NativeRoleMapper() {
    }

    public static NativeRoleRow map(Bundle bundle, String sourceSystem, String extractionRunId) {
        NativeRoleRow row = new NativeRoleRow();

        row.setSourceId(bundle.getId());
        row.setName(bundle.getName());
        row.setDisplayName(bundle.getDisplayName());
        row.setDisplayableName(bundle.getDisplayableName());
        row.setFullName(bundle.getFullName());
        row.setDescription(bundle.getDescription());
        row.setType(bundle.getType());
        row.setAssignmentId(bundle.getAssignmentId());

        row.setActivityEnabled(Boolean.valueOf(bundle.isActivityEnabled()));
        row.setAllowDuplicateAccounts(Boolean.valueOf(bundle.isAllowDuplicateAccounts()));
        row.setAllowMultipleAssignments(Boolean.valueOf(bundle.isAllowMultipleAssignments()));
        row.setAutoPromotion(Boolean.valueOf(bundle.isAutoPromotion()));
        row.setDifferencable(Boolean.valueOf(bundle.isDifferencable()));
        row.setIiqElevatedAccess(Boolean.valueOf(bundle.isIiqElevatedAccess()));
        row.setMergeTemplates(Boolean.valueOf(bundle.isMergeTemplates()));
        row.setOrProfiles(Boolean.valueOf(bundle.isOrProfiles()));
        row.setPendingDelete(Boolean.valueOf(bundle.isPendingDelete()));
        // Assignment condition: has_selector is presence; the IdentitySelector detail below is the
        // actual native birthright / auto-assignment rule (filter / match / population / rule / script).
        IdentitySelector selector = bundle.getSelector();
        row.setHasSelector(Boolean.valueOf(selector != null));
        mapSelector(selector, row);
        row.setRiskScoreWeight(Integer.valueOf(bundle.getRiskScoreWeight()));

        Identity owner = bundle.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }

        row.setActivationDate(toInstant(bundle.getActivationDate()));
        row.setDeactivationDate(toInstant(bundle.getDeactivationDate()));

        Map<String, String> descriptions = bundle.getDescriptions();
        if (descriptions != null) {
            for (Map.Entry<String, String> e : descriptions.entrySet()) {
                if (e.getKey() != null) {
                    row.getDescriptions().put(e.getKey(), e.getValue());
                }
            }
        }

        Attributes<String, Object> attrs = bundle.getAttributes();
        if (attrs != null) {
            for (Object key : attrs.keySet()) {
                if (key != null) {
                    row.getAttributes().put(key.toString(), JsonSafe.toJsonSafe(attrs.get(key)));
                }
            }
        }

        // Additional native fields (source-truth): role type name, member/monitored app names, role risk.
        RoleTypeDefinition rtd = bundle.getRoleTypeDefinition();
        row.setRoleTypeDefinition(rtd == null ? null : rtd.getName());
        row.setApplications(NativeSerialize.jsonArray(appNames(bundle.getApplications())));
        row.setMonitoredApplications(NativeSerialize.jsonArray(appNames(bundle.getMonitoredApplications())));
        row.setScorecard(NativeSerialize.xml(bundle.getScorecard()));

        row.setCreated(toInstant(bundle.getCreated()));
        row.setModified(toInstant(bundle.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    /**
     * Captures the role's {@link IdentitySelector} — the native assignment/birthright condition that
     * {@code has_selector} only flags. Each branch is independent and null-guarded; a selector may
     * carry any combination of a compound filter, a match expression, a population, a rule or a script.
     * Native XML is preserved verbatim for the structured expressions; references are kept as id+name.
     */
    private static void mapSelector(IdentitySelector selector, NativeRoleRow row) {
        if (selector == null) {
            return;
        }
        row.setSelectorSummary(selector.getSummary());
        Map<String, Object> s = row.getSelector();
        s.put("summary", selector.getSummary());
        s.put("empty", Boolean.valueOf(selector.isEmpty()));
        s.put("filter", NativeSerialize.xml(selector.getFilter()));
        s.put("matchExpression", NativeSerialize.xml(selector.getMatchExpression()));
        GroupDefinition population = selector.getPopulation();
        if (population != null) {
            s.put("populationId", population.getId());
            s.put("populationName", population.getName());
        }
        Rule rule = selector.getRule();
        if (rule != null) {
            s.put("ruleId", rule.getId());
            s.put("ruleName", rule.getName());
        }
        Script script = selector.getScript();
        if (script != null) {
            s.put("script", script.getSource());
        }
    }

    private static List<String> appNames(Set<Application> apps) {
        List<String> out = new ArrayList<String>();
        if (apps != null) {
            for (Application a : apps) {
                if (a != null && a.getName() != null) {
                    out.add(a.getName());
                }
            }
        }
        return out;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
