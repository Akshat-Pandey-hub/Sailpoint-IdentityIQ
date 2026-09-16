package com.keyforge.iiq.reconciliation;

/**
 * The result of running one {@link ReferentialCheck}: either CHECKED (with an orphan count and a
 * sample of offending values) or SKIPPED (the child/parent table or column is absent in this schema,
 * so the check could not be evaluated — recorded honestly rather than reported as zero).
 *
 * @param check       the check this finding is for
 * @param status      {@code CHECKED} | {@code SKIPPED}
 * @param skipReason  why it was skipped (null when CHECKED)
 * @param orphanCount number of dangling references (0 when CHECKED and clean; -1 when SKIPPED)
 * @param sampleIds   up to a few offending reference values, for evidence
 */
public record ReconciliationFinding(
        ReferentialCheck check,
        String status,
        String skipReason,
        long orphanCount,
        java.util.List<String> sampleIds) {

    public static final String CHECKED = "CHECKED";
    public static final String SKIPPED = "SKIPPED";

    public static ReconciliationFinding checked(ReferentialCheck c, long orphanCount, java.util.List<String> samples) {
        return new ReconciliationFinding(c, CHECKED, null, orphanCount, samples);
    }

    public static ReconciliationFinding skipped(ReferentialCheck c, String reason) {
        return new ReconciliationFinding(c, SKIPPED, reason, -1, java.util.List.of());
    }

    public boolean hasOrphans() {
        return CHECKED.equals(status) && orphanCount > 0;
    }
}
