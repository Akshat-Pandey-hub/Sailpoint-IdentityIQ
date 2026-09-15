package com.keyforge.iiq.eventlink;

import java.util.Map;
import java.util.Set;

/**
 * Deterministic, conservative resolution of an AuditEvent {@code target} string to exactly one
 * existing Identity / Account / Entitlement. Pure and DB-free: it is constructed with name→id maps
 * (built by {@link EventLinkRepository} from the {@code usr}, {@code account}, {@code entitlement}
 * migration tables) and makes no network or database calls.
 *
 * <p>The audit datasource exposes {@code target} as a display/name string, not an IIQ object id, so
 * resolution is strictly by exact name and never fabricates a link:
 * <ul>
 *   <li>A {@code Type:} prefix is honoured only when the token is a recognised IIQ class. In-scope
 *       prefixes route to their entity ({@code Identity}→usr, {@code Account}/{@code Link}→account,
 *       {@code Entitlement}/{@code ManagedAttribute}→entitlement). Recognised out-of-scope classes
 *       (e.g. {@code Application}, {@code Role}, {@code Workgroup}) yield {@link Status#OUT_OF_SCOPE_TYPE}
 *       — kept, never linked, for this task. An unrecognised prefix (or no colon) is treated as an
 *       untyped name and attempted against Identity only (never cross-matched to account/entitlement).</li>
 *   <li>Resolution requires <b>exactly one</b> distinct id across the entity's two name columns.
 *       Zero → {@link Status#UNRESOLVED} (a preserved dangling reference); more than one →
 *       {@link Status#AMBIGUOUS} (never linked). Both keep the raw target.</li>
 * </ul>
 */
public final class EventLinkResolver {

    public enum Status { RESOLVED, UNRESOLVED, AMBIGUOUS, OUT_OF_SCOPE_TYPE }

    /** In-scope IIQ class prefixes → the entity to resolve against. */
    private static final Map<String, String> IN_SCOPE = Map.of(
            "Identity", "Identity",
            "Account", "Account",
            "Link", "Account",
            "Entitlement", "Entitlement",
            "ManagedAttribute", "Entitlement");

    /** Recognised IIQ class prefixes that are out of scope for the audit→object task. */
    private static final Set<String> OUT_OF_SCOPE = Set.of(
            "Application", "Role", "Bundle", "Workgroup", "GroupDefinition", "Policy",
            "Server", "Configuration", "TaskDefinition", "TaskResult", "Rule",
            "Capability", "Scope", "EmailTemplate", "Form", "QuickLink");

    /**
     * The outcome of resolving one target: the status, the resolved entity type and id (when
     * {@link Status#RESOLVED}), the parsed type prefix (when present), and a short human-readable
     * rule describing how it was decided.
     */
    public record Result(Status status, String targetType, String targetId, String typeHint, String rule) {
    }

    private final Map<String, Set<String>> identityByName;
    private final Map<String, Set<String>> accountByName;
    private final Map<String, Set<String>> entitlementByName;

    public EventLinkResolver(Map<String, Set<String>> identityByName,
                             Map<String, Set<String>> accountByName,
                             Map<String, Set<String>> entitlementByName) {
        this.identityByName = identityByName == null ? Map.of() : identityByName;
        this.accountByName = accountByName == null ? Map.of() : accountByName;
        this.entitlementByName = entitlementByName == null ? Map.of() : entitlementByName;
    }

    /** Resolves a non-blank target string. Callers must handle NULL/blank targets (no reference). */
    public Result resolve(String rawTarget) {
        String target = rawTarget == null ? "" : rawTarget.trim();
        if (target.isEmpty()) {
            // Defensive: NO_TARGET is handled by the caller; treat as unresolved-empty here.
            return new Result(Status.UNRESOLVED, null, null, null, "empty target");
        }

        String typeHint = null;
        String name = target;
        int colon = target.indexOf(':');
        if (colon > 0) {
            String prefix = target.substring(0, colon).trim();
            if (IN_SCOPE.containsKey(prefix) || OUT_OF_SCOPE.contains(prefix)) {
                typeHint = prefix;
                name = target.substring(colon + 1).trim();
            }
        }

        if (typeHint != null && OUT_OF_SCOPE.contains(typeHint)) {
            return new Result(Status.OUT_OF_SCOPE_TYPE, null, null, typeHint,
                    "recognised out-of-scope IIQ type '" + typeHint + "'");
        }

        String entity = typeHint != null ? IN_SCOPE.get(typeHint) : "Identity"; // untyped → Identity only
        switch (entity) {
            case "Identity":
                return resolveAgainst(identityByName, name, "Identity", "usr.username|displayname", typeHint);
            case "Account":
                return resolveAgainst(accountByName, name, "Account",
                        "account.native_identity|account_display_name", typeHint);
            case "Entitlement":
                return resolveAgainst(entitlementByName, name, "Entitlement",
                        "entitlement.value|displayable_name", typeHint);
            default:
                return new Result(Status.UNRESOLVED, null, null, typeHint, "no resolvable entity");
        }
    }

    private static Result resolveAgainst(Map<String, Set<String>> map, String name,
                                         String type, String ruleColumns, String typeHint) {
        Set<String> ids = map.get(name);
        if (ids == null || ids.isEmpty()) {
            return new Result(Status.UNRESOLVED, null, null, typeHint,
                    "no " + type + " match on " + ruleColumns);
        }
        if (ids.size() > 1) {
            return new Result(Status.AMBIGUOUS, null, null, typeHint,
                    "ambiguous: " + ids.size() + " " + type + " matches on " + ruleColumns);
        }
        return new Result(Status.RESOLVED, type, ids.iterator().next(), typeHint, ruleColumns);
    }
}
