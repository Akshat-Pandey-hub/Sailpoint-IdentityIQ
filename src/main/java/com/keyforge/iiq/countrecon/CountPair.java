package com.keyforge.iiq.countrecon;

/**
 * One domain that is extracted by <b>both</b> pipelines — the normalized PostgreSQL table and the
 * Parquet analytics dataset — so their row counts can be cross-checked. Only domains present in both
 * stores are paired (verified against the repository DDLs and {@link
 * com.keyforge.iiq.parquet.ParquetDatasets}); domains unique to one store are intentionally omitted.
 *
 * @param domain          logical domain name (report key)
 * @param pgTable         normalized PostgreSQL table (unqualified; schema applied at run time)
 * @param parquetDataset  Parquet dataset directory name
 */
public record CountPair(String domain, String pgTable, String parquetDataset) {
}
