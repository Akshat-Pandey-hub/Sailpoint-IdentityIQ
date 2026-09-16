package com.keyforge.iiq.reconciliation;

/**
 * One referential-integrity check: child rows whose {@code childColumn} (when non-null) has no
 * matching parent row on {@code parentColumn}. Every check references columns that actually exist in
 * the project's PostgreSQL schema (verified against the repository DDLs); nothing here is speculative.
 *
 * <p>A dangling reference is a real data-quality defect the PDF's reconciliation plane calls for —
 * for example an {@code entitlementassignment.identity_id} that points at an identity no longer in
 * {@code usr}. The check is expressed once, declaratively, and executed as a guarded anti-join.
 *
 * @param childTable   table holding the reference (unqualified; schema is applied at run time)
 * @param childColumn  the referencing column
 * @param parentTable  the referenced table
 * @param parentColumn the referenced (key) column
 * @param severity     {@code HIGH} for core identity/access edges, {@code MEDIUM} otherwise
 */
public record ReferentialCheck(
        String childTable,
        String childColumn,
        String parentTable,
        String parentColumn,
        String severity) {

    /** Stable, human-readable name, unique by construction (child.col -> parent.col). */
    public String name() {
        return childTable + "." + childColumn + " -> " + parentTable + "." + parentColumn;
    }
}
