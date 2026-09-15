package com.keyforge.iiq.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Account;
import com.keyforge.iiq.model.AccountEntitlementAssignment;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionSource;
import com.keyforge.iiq.model.AccountEntitlementAssignment.ResolutionStatus;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Identity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Derives Account &rarr; Entitlement assignments from data already extracted by
 * the (unmodified) Account, Entitlement and Identity services. This is a pure
 * transformation layer: it performs no HTTP and holds no configuration.
 *
 * <p><b>How the relationship is resolved (fully generic, no hardcoded attribute
 * or application names):</b>
 * <ol>
 *     <li>The Entitlement catalogue is indexed. For every application we derive
 *         the set of <i>entitlement-bearing attribute names</i> straight from the
 *         catalogue ({@code entitlement.attribute}). So "groups", "posixgroups",
 *         "roles", ... are discovered from data, never hardcoded, and ordinary
 *         multi-valued attributes (e.g. proxyAddresses) are ignored because they
 *         are not catalogue attributes.</li>
 *     <li><b>Primary pass (account-driven):</b> for each Account we walk its
 *         preserved application-specific attributes; for each entitlement-bearing
 *         attribute we explode its array/scalar into one candidate value each and
 *         resolve that value to a catalogue Entitlement by
 *         (application + attribute + value), falling back to (application + value).</li>
 *     <li><b>Enrichment/validation/fallback pass (user-extension):</b> the
 *         SailPoint User-extension entitlement references carry an explicit
 *         Entitlement {@code $ref} (id). They confirm account-derived assignments
 *         and can resolve ones the catalogue value-match missed.</li>
 * </ol>
 *
 * <p>Assignments are de-duplicated on (accountId, application, attribute, value);
 * when both passes agree the sources are unioned (a validated assignment).
 * Unresolved values are kept (entitlementId = null, status = UNRESOLVED), never
 * dropped. No provisioning mechanism is inferred.
 */
public class AccountEntitlementAssignmentService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public List<AccountEntitlementAssignment> build(List<Account> accounts, List<Entitlement> entitlements) {
        return build(accounts, entitlements, List.of());
    }

    public List<AccountEntitlementAssignment> build(List<Account> accounts,
                                                    List<Entitlement> entitlements,
                                                    List<Identity> identities) {
        List<Account> safeAccounts = accounts == null ? List.of() : accounts;
        List<Entitlement> safeEntitlements = entitlements == null ? List.of() : entitlements;
        List<Identity> safeIdentities = identities == null ? List.of() : identities;

        Catalog catalog = new Catalog(safeEntitlements);
        AccountIndex accountIndex = new AccountIndex(safeAccounts);

        // Insertion-ordered so output is deterministic; key merges the two passes.
        Map<String, Mutable> merged = new LinkedHashMap<>();

        // Pass 1 — account-driven (primary).
        for (Account account : safeAccounts) {
            deriveFromAccount(account, catalog, merged);
        }

        // Pass 2 — user-extension (enrichment / validation / fallback).
        for (Identity identity : safeIdentities) {
            deriveFromUserExtension(identity, catalog, accountIndex, merged);
        }

        List<AccountEntitlementAssignment> result = new ArrayList<>(merged.size());
        for (Mutable m : merged.values()) {
            result.add(m.toImmutable());
        }
        return result;
    }

    // --- pass 1: account-driven ---------------------------------------------

    private void deriveFromAccount(Account account, Catalog catalog, Map<String, Mutable> merged) {
        Account.Ref appRef = account.getApplication();
        String appId = appRef == null ? null : appRef.getValue();
        String appName = appRef == null ? null : appRef.getDisplayName();

        Set<String> entitlementAttrs = catalog.entitlementAttributesFor(appId, appName);
        if (entitlementAttrs.isEmpty()) {
            // No catalogue entitlements for this application -> we cannot tell which
            // attributes are entitlements without hardcoding. Skip (never guess).
            return;
        }

        String accountId = account.getId();
        String accountName = account.getNativeIdentity() != null
                ? account.getNativeIdentity() : account.getDisplayName();
        Account.Ref idRef = account.getIdentity();
        String identityId = idRef == null ? null : idRef.getValue();
        String identityName = idRef == null ? null : idRef.getDisplayName();

        List<Candidate> candidates = new ArrayList<>();
        walk(account.getAdditionalAttributes(), entitlementAttrs, candidates);

        for (Candidate candidate : candidates) {
            Entitlement ent = null;
            String value = candidate.value;

            if (value != null) {
                ent = catalog.resolve(appId, appName, candidate.attribute, value);
            } else if (candidate.raw != null && candidate.raw.isObject()) {
                // Object element: try each of its scalar values against the catalogue.
                for (String scalar : scalarTextValues(candidate.raw)) {
                    Entitlement match = catalog.resolve(appId, appName, candidate.attribute, scalar);
                    if (match != null) {
                        ent = match;
                        value = scalar;
                        break;
                    }
                }
            }

            ObjectNode custom = baseCustom(candidate.attribute, value, appId, appName);
            if (candidate.raw != null && !candidate.raw.isValueNode()) {
                custom.set("rawValue", candidate.raw.deepCopy());
            }

            Mutable m = new Mutable();
            m.accountId = accountId;
            m.accountName = accountName;
            m.identityId = identityId;
            m.identityDisplayName = identityName;
            m.applicationId = appId;
            m.applicationName = appName;
            m.sourceAttribute = candidate.attribute;
            m.entitlementValue = value;
            m.customAttributes = custom;
            m.sources.add(ResolutionSource.ACCOUNT_ATTRIBUTE);
            if (ent != null) {
                m.entitlementId = ent.getId();
                m.entitlementDisplayName = ent.getDisplayableName();
                m.entitlementType = ent.getType();
                m.status = ResolutionStatus.RESOLVED;
            } else {
                m.status = ResolutionStatus.UNRESOLVED;
            }

            addOrMerge(merged, m);
        }
    }

    /**
     * Collects candidate (attribute, value) pairs by recursively walking the
     * account's preserved attributes. Only fields whose name matches an
     * entitlement-bearing attribute are exploded into candidates; other objects
     * and arrays are descended into so nested application-schema wrappers are
     * reached. Matched attributes are not descended into further.
     */
    private static void walk(JsonNode node, Set<String> entitlementAttrs, List<Candidate> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                if (entitlementAttrs.contains(normKey(key))) {
                    collectValues(key, value, out);
                } else if (value.isObject() || value.isArray()) {
                    walk(value, entitlementAttrs, out);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (element.isObject() || element.isArray()) {
                    walk(element, entitlementAttrs, out);
                }
            }
        }
    }

    private static void collectValues(String attribute, JsonNode value, List<Candidate> out) {
        if (value.isArray()) {
            for (JsonNode element : value) {
                if (element.isValueNode()) {
                    out.add(new Candidate(attribute, element.asText(), element));
                } else if (element.isObject()) {
                    out.add(new Candidate(attribute, null, element));
                } else if (element.isArray()) {
                    for (JsonNode inner : element) {
                        if (inner.isValueNode()) {
                            out.add(new Candidate(attribute, inner.asText(), inner));
                        }
                    }
                }
            }
        } else if (value.isValueNode()) {
            out.add(new Candidate(attribute, value.asText(), value));
        } else if (value.isObject()) {
            out.add(new Candidate(attribute, null, value));
        }
    }

    // --- pass 2: user-extension ---------------------------------------------

    private void deriveFromUserExtension(Identity identity,
                                         Catalog catalog,
                                         AccountIndex accountIndex,
                                         Map<String, Mutable> merged) {
        for (JsonNode ref : identity.getEntitlementReferences()) {
            String appNameRef = text(ref, "application");
            String accountNameRef = text(ref, "accountName");
            String nativeValue = text(ref, "display");   // the entitlement native value
            String attribute = text(ref, "value");       // the account attribute name
            String entId = lastPathSegment(text(ref, "$ref"));

            Account account = accountIndex.find(appNameRef, accountNameRef);
            String accountId = account == null ? null : account.getId();
            String accountName = account != null && account.getNativeIdentity() != null
                    ? account.getNativeIdentity() : accountNameRef;
            Account.Ref appRef = account == null ? null : account.getApplication();
            String appId = appRef == null ? null : appRef.getValue();
            String appName = appRef != null ? appRef.getDisplayName() : appNameRef;
            Account.Ref idRef = account == null ? null : account.getIdentity();
            String identityId = idRef != null ? idRef.getValue() : identity.getId();
            String identityName = idRef != null ? idRef.getDisplayName() : identity.getDisplayName();

            Entitlement ent = entId == null ? null : catalog.byId(entId);

            ObjectNode custom = baseCustom(attribute, nativeValue, appId, appName);
            custom.set("userExtensionEntry", ref.deepCopy());
            if (account == null) {
                custom.put("unmatchedAccount", true);
            }

            Mutable m = new Mutable();
            m.accountId = accountId;
            m.accountName = accountName;
            m.identityId = identityId;
            m.identityDisplayName = identityName;
            m.applicationId = appId;
            m.applicationName = appName;
            m.sourceAttribute = attribute;
            m.entitlementValue = nativeValue;
            m.customAttributes = custom;
            m.sources.add(ResolutionSource.USER_EXTENSION);
            if (entId != null) {
                m.entitlementId = entId;
                m.entitlementDisplayName = ent != null ? ent.getDisplayableName() : null;
                m.entitlementType = ent != null ? ent.getType() : null;
                m.status = ResolutionStatus.RESOLVED;
            } else {
                m.status = ResolutionStatus.UNRESOLVED;
            }

            addOrMerge(merged, m);
        }
    }

    // --- merge / dedup ------------------------------------------------------

    private static void addOrMerge(Map<String, Mutable> merged, Mutable incoming) {
        String key = incoming.key();
        Mutable existing = merged.get(key);
        if (existing == null) {
            merged.put(key, incoming);
        } else {
            existing.mergeFrom(incoming);
        }
    }

    // --- small helpers ------------------------------------------------------

    private static ObjectNode baseCustom(String attribute, String value, String appId, String appName) {
        ObjectNode custom = JSON.objectNode();
        if (attribute != null) {
            custom.put("sourceAttribute", attribute);
        }
        if (value != null) {
            custom.put("nativeValue", value);
        }
        if (appId != null) {
            custom.put("applicationId", appId);
        }
        if (appName != null) {
            custom.put("applicationName", appName);
        }
        return custom;
    }

    private static List<String> scalarTextValues(JsonNode object) {
        List<String> values = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            JsonNode v = fields.next().getValue();
            if (v.isValueNode()) {
                values.add(v.asText());
            }
        }
        return values;
    }

    private static String lastPathSegment(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String s = ref;
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int slash = s.lastIndexOf('/');
        String segment = slash >= 0 ? s.substring(slash + 1) : s;
        return segment.isBlank() ? null : segment;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    static String normKey(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // --- inner types --------------------------------------------------------

    /** A single (attribute, value) pair discovered on an account. */
    private static final class Candidate {
        final String attribute;
        final String value;   // null when the source element is an object
        final JsonNode raw;

        Candidate(String attribute, String value, JsonNode raw) {
            this.attribute = attribute;
            this.value = value;
            this.raw = raw;
        }
    }

    /** Mutable accumulator used while merging the two passes. */
    private static final class Mutable {
        String accountId;
        String accountName;
        String identityId;
        String identityDisplayName;
        String applicationId;
        String applicationName;
        String sourceAttribute;
        String entitlementValue;
        String entitlementId;
        String entitlementDisplayName;
        String entitlementType;
        ResolutionStatus status = ResolutionStatus.UNRESOLVED;
        final EnumSet<ResolutionSource> sources = EnumSet.noneOf(ResolutionSource.class);
        ObjectNode customAttributes = JSON.objectNode();

        String key() {
            String appPart = applicationId != null ? "id:" + normKey(applicationId) : "name:" + normKey(applicationName);
            return normKey(accountId) + " " + appPart + " "
                    + normKey(sourceAttribute) + " " + normKey(entitlementValue);
        }

        void mergeFrom(Mutable other) {
            sources.addAll(other.sources);
            // Prefer a resolved entitlement id from whichever pass has one.
            if (entitlementId == null && other.entitlementId != null) {
                entitlementId = other.entitlementId;
                entitlementDisplayName = other.entitlementDisplayName;
                entitlementType = other.entitlementType;
                status = ResolutionStatus.RESOLVED;
            }
            // Fill any missing anchor fields.
            if (accountId == null) {
                accountId = other.accountId;
            }
            if (accountName == null) {
                accountName = other.accountName;
            }
            if (identityId == null) {
                identityId = other.identityId;
            }
            if (identityDisplayName == null) {
                identityDisplayName = other.identityDisplayName;
            }
            if (applicationId == null) {
                applicationId = other.applicationId;
            }
            if (applicationName == null) {
                applicationName = other.applicationName;
            }
            if (entitlementDisplayName == null) {
                entitlementDisplayName = other.entitlementDisplayName;
            }
            if (entitlementType == null) {
                entitlementType = other.entitlementType;
            }
            // Preserve provenance detail from the other pass without overwriting.
            Iterator<Map.Entry<String, JsonNode>> it = other.customAttributes.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!customAttributes.has(e.getKey())) {
                    customAttributes.set(e.getKey(), e.getValue());
                }
            }
        }

        AccountEntitlementAssignment toImmutable() {
            return new AccountEntitlementAssignment(
                    accountId, accountName, identityId, identityDisplayName,
                    applicationId, applicationName, sourceAttribute, entitlementValue,
                    entitlementId, entitlementDisplayName, entitlementType,
                    status, sources, customAttributes);
        }
    }

    /** Indexes the Entitlement catalogue for resolution, keyed by application id and name. */
    private static final class Catalog {
        private final Map<String, Set<String>> attrsByApp = new LinkedHashMap<>();
        private final Map<String, Map<String, Entitlement>> byAppAttrValue = new LinkedHashMap<>();
        private final Map<String, Map<String, Entitlement>> byAppValue = new LinkedHashMap<>();
        private final Map<String, Entitlement> byId = new LinkedHashMap<>();

        Catalog(List<Entitlement> entitlements) {
            for (Entitlement e : entitlements) {
                Entitlement.ApplicationRef app = e.getApplication();
                String appId = app == null ? null : app.getValue();
                String appName = app == null ? null : app.getDisplayName();
                String attr = e.getAttribute();
                String value = e.getValue();

                for (String appKey : appKeys(appId, appName)) {
                    if (attr != null) {
                        attrsByApp.computeIfAbsent(appKey, k -> new java.util.HashSet<>()).add(normKey(attr));
                        byAppAttrValue.computeIfAbsent(appKey, k -> new LinkedHashMap<>())
                                .putIfAbsent(normKey(attr) + " " + normKey(value), e);
                    }
                    byAppValue.computeIfAbsent(appKey, k -> new LinkedHashMap<>())
                            .putIfAbsent(normKey(value), e);
                }
                if (e.getId() != null) {
                    byId.putIfAbsent(e.getId(), e);
                }
            }
        }

        Set<String> entitlementAttributesFor(String appId, String appName) {
            Set<String> attrs = new java.util.HashSet<>();
            for (String appKey : appKeys(appId, appName)) {
                Set<String> forKey = attrsByApp.get(appKey);
                if (forKey != null) {
                    attrs.addAll(forKey);
                }
            }
            return attrs;
        }

        Entitlement resolve(String appId, String appName, String attribute, String value) {
            if (value == null) {
                return null;
            }
            for (String appKey : appKeys(appId, appName)) {
                Map<String, Entitlement> attrValue = byAppAttrValue.get(appKey);
                if (attribute != null && attrValue != null) {
                    Entitlement e = attrValue.get(normKey(attribute) + " " + normKey(value));
                    if (e != null) {
                        return e;
                    }
                }
            }
            for (String appKey : appKeys(appId, appName)) {
                Map<String, Entitlement> valueOnly = byAppValue.get(appKey);
                if (valueOnly != null) {
                    Entitlement e = valueOnly.get(normKey(value));
                    if (e != null) {
                        return e;
                    }
                }
            }
            return null;
        }

        Entitlement byId(String id) {
            return id == null ? null : byId.get(id);
        }

        private static List<String> appKeys(String appId, String appName) {
            List<String> keys = new ArrayList<>(2);
            if (appId != null && !appId.isBlank()) {
                keys.add("id:" + normKey(appId));
            }
            if (appName != null && !appName.isBlank()) {
                keys.add("name:" + normKey(appName));
            }
            return keys;
        }
    }

    /** Indexes accounts by (application, accountName) for user-extension matching. */
    private static final class AccountIndex {
        private final Map<String, Account> index = new LinkedHashMap<>();

        AccountIndex(List<Account> accounts) {
            for (Account a : accounts) {
                Account.Ref app = a.getApplication();
                String appId = app == null ? null : app.getValue();
                String appName = app == null ? null : app.getDisplayName();
                for (String appKey : Catalog.appKeys(appId, appName)) {
                    putIfAbsent(appKey, a.getNativeIdentity(), a);
                    putIfAbsent(appKey, a.getDisplayName(), a);
                }
            }
        }

        private void putIfAbsent(String appKey, String name, Account account) {
            if (name == null || name.isBlank()) {
                return;
            }
            index.putIfAbsent(appKey + " " + normKey(name), account);
        }

        Account find(String appName, String accountName) {
            if (accountName == null) {
                return null;
            }
            // The user-extension only carries the application NAME.
            String key = "name:" + normKey(appName) + " " + normKey(accountName);
            return index.get(key);
        }
    }
}
