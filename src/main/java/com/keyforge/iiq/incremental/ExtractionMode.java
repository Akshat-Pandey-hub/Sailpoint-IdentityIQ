package com.keyforge.iiq.incremental;

/**
 * Selects how a {@code -db} extraction command treats already-captured source records.
 *
 * <ul>
 *   <li>{@link #FULL} — process every record the interface returns (the historical, live-validated
 *       behaviour). The default when no flag is given, so existing runbooks are unchanged.</li>
 *   <li>{@link #INCREMENTAL} — process only records whose <b>authoritative source-side change time</b>
 *       (e.g. SCIM {@code meta.lastModified}) is at or after the persisted per-entity watermark.</li>
 * </ul>
 *
 * <p>Important: no verified IdentityIQ read interface exposes a server-side "modified since" query
 * filter (SCIM, classic UI/REST all return the full result set with per-record change timestamps but
 * no such filter parameter). Incremental mode is therefore implemented <b>client-side</b>: the same
 * pages are read, but only changed records are persisted. It reduces database writes and downstream
 * churn, not IdentityIQ reads. This is documented behaviour, not a limitation hidden from callers.
 */
public enum ExtractionMode {

    FULL,
    INCREMENTAL;

    /** {@code --incremental} selects incremental; {@code --full} or absence selects full (default). */
    public static ExtractionMode fromArgs(String[] args) {
        if (args == null) {
            return FULL;
        }
        for (String a : args) {
            if (a == null) {
                continue;
            }
            String flag = a.trim();
            if (flag.equalsIgnoreCase("--incremental") || flag.equalsIgnoreCase("--delta")) {
                return INCREMENTAL;
            }
            if (flag.equalsIgnoreCase("--full") || flag.equalsIgnoreCase("--initial")) {
                return FULL;
            }
        }
        return FULL;
    }

    public boolean isIncremental() {
        return this == INCREMENTAL;
    }
}
