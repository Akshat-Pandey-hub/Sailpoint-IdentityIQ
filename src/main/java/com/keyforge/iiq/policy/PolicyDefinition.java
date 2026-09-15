package com.keyforge.iiq.policy;

/**
 * A SailPoint IdentityIQ Policy definition, as returned by the Policy management grid
 * ({@code define/policy/policiesDataSource.json}). The authoritative field set comes from the
 * grid's own metadata ({@code id, name, type, state, description}). Per-constraint detail is not
 * in this list grid; it would require the policy-editor datasource / JDBC (deferred, not fabricated).
 *
 * @param id          policy id
 * @param name        policy name
 * @param type        policy type (e.g. SOD / Activity / Risk)
 * @param state       policy state (enabled/disabled)
 * @param description description
 */
public record PolicyDefinition(
        String id,
        String name,
        String type,
        String state,
        String description) {
}
