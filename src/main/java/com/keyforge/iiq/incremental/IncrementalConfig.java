package com.keyforge.iiq.incremental;

import com.keyforge.iiq.config.ConfigException;

import java.time.Duration;

/**
 * Configuration for incremental extraction. Currently just the <b>overlap window</b> required by the
 * KeyForge Connector Design &sect;4.1: incremental sync pulls objects where
 * {@code modified >= watermark - overlap_window}. The overlap absorbs source clock skew and
 * late-committed records; the existing idempotent upserts absorb the resulting duplicate re-processing.
 *
 * <p>Resolution follows the same convention as {@link com.keyforge.iiq.config.AppConfig} and
 * {@link com.keyforge.iiq.config.PgConfig}: environment variable first, then JVM system property.
 * No new configuration system is introduced.
 *
 * <p><b>Default:</b> the PDF does <i>not</i> specify a particular overlap duration, so
 * {@value #DEFAULT_OVERLAP_MINUTES} minutes is a KeyForge project default chosen here as a
 * conservative skew/late-commit cushion — not a value taken from the PDF. It is safe to enlarge
 * because reprocessing inside the window is idempotent.
 */
public final class IncrementalConfig {

    /** Env var / system property naming the overlap window, in whole minutes ({@code >= 0}). */
    public static final String KEY_OVERLAP_MINUTES = "INCREMENTAL_OVERLAP_MINUTES";

    /** KeyForge project default (not specified by the PDF). */
    public static final long DEFAULT_OVERLAP_MINUTES = 5;

    private IncrementalConfig() {
    }

    /** The configured overlap window, resolved from the environment (env, then system property). */
    public static Duration overlapWindow() {
        return parseOverlapMinutes(resolve(KEY_OVERLAP_MINUTES));
    }

    /**
     * Pure parse of the overlap setting (unit-testable, no environment access).
     *
     * @param raw the raw configured value, or {@code null}/blank to take the default
     * @return the overlap {@link Duration}
     * @throws ConfigException if the value is present but not a whole number of minutes {@code >= 0}
     */
    public static Duration parseOverlapMinutes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Duration.ofMinutes(DEFAULT_OVERLAP_MINUTES);
        }
        String s = raw.trim();
        long minutes;
        try {
            minutes = Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ConfigException(KEY_OVERLAP_MINUTES
                    + " must be a whole number of minutes (>= 0) but was: '" + s + "'");
        }
        if (minutes < 0) {
            throw new ConfigException(KEY_OVERLAP_MINUTES + " must be >= 0 but was: " + minutes);
        }
        return Duration.ofMinutes(minutes);
    }

    private static String resolve(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromSysProp = System.getProperty(key);
        if (fromSysProp != null && !fromSysProp.isBlank()) {
            return fromSysProp.trim();
        }
        return null;
    }
}
