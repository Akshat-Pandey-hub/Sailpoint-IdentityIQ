package com.keyforge.iiq.lineage;

/**
 * Per-table lineage mapping: how to derive the PDF lineage envelope for one normalized domain table.
 * Only the primary key and the coarse metadata (IIQ object type, producing interface) are required;
 * the natural-key / created / modified columns are optional and are always existence-guarded at run
 * time, so an absent column becomes a NULL envelope value rather than an error — nothing is fabricated.
 *
 * @param table         unqualified domain table name (schema applied at run time)
 * @param pk            primary-key column (used as {@code record_pk} and, when no {@code source_id}
 *                      column exists, as {@code src_object_id})
 * @param srcObjectType the IIQ object class this table represents (PDF {@code src_object_type}
 *                      taxonomy, e.g. {@code sailpoint.object.Identity}) or a {@code derived:*} tag
 * @param srcInterface  the interface that produced the table (scim | ui-rest | classic-rest |
 *                      classic-ui | derived)
 * @param natCol        natural-key column (username/name/request_number…), or null
 * @param createdCol    source-created timestamp column, or null
 * @param modifiedCol   source-modified timestamp column, or null
 */
public record LineageSource(
        String table,
        String pk,
        String srcObjectType,
        String srcInterface,
        String natCol,
        String createdCol,
        String modifiedCol) {
}
