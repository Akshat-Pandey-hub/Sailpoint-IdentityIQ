package com.keyforge.iiq.parquet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Parquet dataset: its name (used as the output subdirectory and REST resource) and its business
 * columns. The full physical schema is the business columns followed by the {@link Lineage} envelope.
 */
public record DatasetSpec(String name, List<Column> businessColumns) {

    /**
     * Business columns + the twelve lineage columns, in physical order. If a dataset legitimately carries
     * a business column whose name coincides with a lineage column (e.g. an event-link's own
     * {@code src_object_type}/{@code src_object_id} endpoint), the business column wins and the duplicate
     * lineage column is dropped — so the physical schema never declares the same column twice. This is a
     * no-op for every dataset whose business columns don't collide with the lineage names.
     */
    public List<Column> allColumns() {
        List<Column> all = new ArrayList<>(businessColumns);
        Set<String> seen = new HashSet<>();
        for (Column c : businessColumns) {
            seen.add(c.name());
        }
        for (Column c : Lineage.COLUMNS) {
            if (seen.add(c.name())) {
                all.add(c);
            }
        }
        return all;
    }
}
