package com.keyforge.iiq.policy;

/**
 * A row of the project-owned {@code kf_policy} table. Fields are the authoritative Policy grid
 * columns (id/name/type/state/description). Per-constraint child records are not available from
 * the list grid and are intentionally absent (not fabricated).
 */
public record PolicyRow(
        String policyid,
        String name,
        String type,
        String state,
        String description) {
}
