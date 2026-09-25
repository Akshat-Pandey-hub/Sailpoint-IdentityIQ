package com.keyforge.iiq.rest;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves which single Parquet file a query endpoint reads for a dataset. The REST/SCIM datasets and the
 * native datasets differ ONLY in this resolution: REST/SCIM datasets are snapshot part files
 * ({@code part-<run>.parquet}, latest by default) while native datasets are one stable current-state file
 * ({@code current.parquet}). Everything downstream (validation, SQL, execution) is shared and identical.
 */
public interface FileResolver {

    /**
     * The Parquet file to query, or empty if the dataset has no extracted file yet.
     *
     * @param dataset the (already validated) dataset name
     * @param run     an optional specific extraction run id; resolvers that have no per-run files ignore it
     */
    Optional<Path> resolve(String dataset, String run);
}
