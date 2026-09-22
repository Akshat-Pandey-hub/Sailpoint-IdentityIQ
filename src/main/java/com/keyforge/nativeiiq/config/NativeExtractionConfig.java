package com.keyforge.nativeiiq.config;

import java.util.UUID;

/**
 * Configuration for the native extraction, resolved env-first (env var, then JVM system property),
 * mirroring the main project's convention. Pure (no SailPoint dependency), so it is unit-testable.
 * No PostgreSQL settings here yet — native persistence/transport is a later phase; this phase only
 * declares the run parameters.
 */
public final class NativeExtractionConfig {

    public static final String KEY_SOURCE_SYSTEM = "IIQ_NATIVE_SOURCE_SYSTEM";
    public static final String KEY_BATCH_SIZE = "IIQ_NATIVE_BATCH_SIZE";
    public static final String KEY_IDENTITY_LIMIT = "IIQ_NATIVE_IDENTITY_LIMIT";
    public static final String KEY_OUTPUT_MODE = "IIQ_NATIVE_OUTPUT_MODE";

    public static final String DEFAULT_SOURCE_SYSTEM = "IdentityIQ";
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DEFAULT_IDENTITY_LIMIT = 0;   // 0 = no limit (extract all)
    public static final String DEFAULT_OUTPUT_MODE = "IN_MEMORY"; // placeholder; transport decided later

    private final String sourceSystem;
    private final String extractionRunId;
    private final int batchSize;
    private final int identityLimit;
    private final String outputMode;

    public NativeExtractionConfig(String sourceSystem, String extractionRunId,
                                  int batchSize, int identityLimit, String outputMode) {
        this.sourceSystem = blankTo(sourceSystem, DEFAULT_SOURCE_SYSTEM);
        this.extractionRunId = blankTo(extractionRunId, UUID.randomUUID().toString());
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.identityLimit = identityLimit >= 0 ? identityLimit : DEFAULT_IDENTITY_LIMIT;
        this.outputMode = blankTo(outputMode, DEFAULT_OUTPUT_MODE);
    }

    /** Resolve from environment / system properties, generating a run id. */
    public static NativeExtractionConfig load() {
        return new NativeExtractionConfig(
                resolve(KEY_SOURCE_SYSTEM),
                null,
                parseInt(resolve(KEY_BATCH_SIZE), DEFAULT_BATCH_SIZE),
                parseInt(resolve(KEY_IDENTITY_LIMIT), DEFAULT_IDENTITY_LIMIT),
                resolve(KEY_OUTPUT_MODE));
    }

    /** Build from explicit overrides (e.g. IIQ task arguments), falling back to env then defaults. */
    public static NativeExtractionConfig of(String sourceSystem, Integer batchSize, Integer identityLimit,
                                            String outputMode, String extractionRunId) {
        return new NativeExtractionConfig(
                sourceSystem != null ? sourceSystem : resolve(KEY_SOURCE_SYSTEM),
                extractionRunId,
                batchSize != null ? batchSize.intValue() : parseInt(resolve(KEY_BATCH_SIZE), DEFAULT_BATCH_SIZE),
                identityLimit != null ? identityLimit.intValue() : parseInt(resolve(KEY_IDENTITY_LIMIT), DEFAULT_IDENTITY_LIMIT),
                outputMode != null ? outputMode : resolve(KEY_OUTPUT_MODE));
    }

    public String getSourceSystem() { return sourceSystem; }
    public String getExtractionRunId() { return extractionRunId; }
    public int getBatchSize() { return batchSize; }
    public int getIdentityLimit() { return identityLimit; }
    public String getOutputMode() { return outputMode; }
    public boolean hasIdentityLimit() { return identityLimit > 0; }

    private static String resolve(String key) {
        String v = System.getenv(key);
        if (v == null || v.trim().isEmpty()) {
            v = System.getProperty(key);
        }
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    private static int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String blankTo(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }
}
