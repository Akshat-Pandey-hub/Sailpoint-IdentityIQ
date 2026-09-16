package com.keyforge.iiq.lineage;

/**
 * Pure builder for the per-table lineage backfill statement. Identifiers come only from the fixed
 * {@link LineageCatalog} (never caller input) and the schema is validated upstream, so they are
 * inlined; there are no bound value parameters. Every optional column is gated by a boolean the
 * repository computes from {@code information_schema}, so an absent column yields a {@code NULL}
 * envelope value rather than invalid SQL.
 *
 * <p>{@code src_object_id} prefers a retained raw {@code source_id}, else the canonical PK.
 * {@code record_hash} is {@code md5} over the row's JSON with the volatile {@code extracted_at}
 * removed, so an unchanged source record hashes the same across runs. {@code extraction_run_id},
 * {@code src_event_ts} and {@code raw_ref} are always NULL (not retained per-row / no RAW zone).
 */
public final class RecordLineageSql {

    public static final String TABLE = "kf_record_lineage";

    private RecordLineageSql() {
    }

    public static String createTable(String schema) {
        return "CREATE TABLE IF NOT EXISTS " + schema + "." + TABLE + " ("
                + "lineage_id text PRIMARY KEY, "
                + "table_name text, record_pk text, "
                + "src_system text, src_object_type text, src_object_id text, src_natural_key text, "
                + "src_created timestamptz, src_modified timestamptz, src_event_ts timestamptz, "
                + "extracted_at timestamptz, extraction_run_id text, src_interface text, "
                + "record_hash text, raw_ref text, derived_at timestamptz)";
    }

    public static String backfill(String schema, LineageSource s, boolean hasSourceId, boolean hasNat,
                                  boolean hasCreated, boolean hasModified, boolean hasExtracted) {
        String tbl = schema + "." + s.table();
        String pk = "t." + s.pk();
        String idExpr = hasSourceId ? "COALESCE(t.source_id, " + pk + "::text)" : pk + "::text";
        String natExpr = (hasNat && s.natCol() != null) ? "t." + s.natCol() + "::text" : "NULL";
        String createdExpr = (hasCreated && s.createdCol() != null) ? "t." + s.createdCol() : "NULL";
        String modifiedExpr = (hasModified && s.modifiedCol() != null) ? "t." + s.modifiedCol() : "NULL";
        String extractedExpr = hasExtracted ? "t.extracted_at" : "now()";
        String hashExpr = hasExtracted ? "md5((to_jsonb(t) - 'extracted_at')::text)" : "md5(to_jsonb(t)::text)";

        return "INSERT INTO " + schema + "." + TABLE + " (lineage_id, table_name, record_pk, src_system, "
                + "src_object_type, src_object_id, src_natural_key, src_created, src_modified, src_event_ts, "
                + "extracted_at, extraction_run_id, src_interface, record_hash, raw_ref, derived_at) "
                + "SELECT md5('" + s.table() + "|' || " + pk + "::text), '" + s.table() + "', " + pk + "::text, "
                + "'" + LineageCatalog.SRC_SYSTEM + "', '" + s.srcObjectType() + "', " + idExpr + ", " + natExpr + ", "
                + createdExpr + ", " + modifiedExpr + ", NULL, " + extractedExpr + ", NULL, '" + s.srcInterface() + "', "
                + hashExpr + ", NULL, now() "
                + "FROM " + tbl + " t "
                + "ON CONFLICT (lineage_id) DO UPDATE SET table_name=EXCLUDED.table_name, record_pk=EXCLUDED.record_pk, "
                + "src_system=EXCLUDED.src_system, src_object_type=EXCLUDED.src_object_type, "
                + "src_object_id=EXCLUDED.src_object_id, src_natural_key=EXCLUDED.src_natural_key, "
                + "src_created=EXCLUDED.src_created, src_modified=EXCLUDED.src_modified, "
                + "extracted_at=EXCLUDED.extracted_at, src_interface=EXCLUDED.src_interface, "
                + "record_hash=EXCLUDED.record_hash, derived_at=EXCLUDED.derived_at";
    }
}
