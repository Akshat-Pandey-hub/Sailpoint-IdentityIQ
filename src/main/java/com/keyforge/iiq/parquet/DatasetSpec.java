package com.keyforge.iiq.parquet;

import java.util.ArrayList;
import java.util.List;

/**
 * A Parquet dataset: its name (used as the output subdirectory and REST resource) and its business
 * columns. The full physical schema is the business columns followed by the {@link Lineage} envelope.
 */
public record DatasetSpec(String name, List<Column> businessColumns) {

    /** Business columns + the twelve lineage columns, in physical order. */
    public List<Column> allColumns() {
        List<Column> all = new ArrayList<>(businessColumns);
        all.addAll(Lineage.COLUMNS);
        return all;
    }
}
