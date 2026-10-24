package com.keyforge.iiq.nativeparquet;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Supplies current source rows for the shared Parquet comparison and writer pipeline. */
public interface ParquetRowSource {

    List<Row> read(NativeParquetDatasets.Def dataset) throws Exception;

    record Row(String primaryKey, Map<String, Object> businessValues, Instant created, Instant modified) {
    }
}
