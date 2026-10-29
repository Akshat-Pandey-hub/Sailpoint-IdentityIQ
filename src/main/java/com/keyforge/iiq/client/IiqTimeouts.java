package com.keyforge.iiq.client;

import java.time.Duration;

/**
 * How long the KF Agent waits for one IdentityIQ call. Set {@value #ENV} (seconds) to change it; when it is not set
 * the agent waits {@value #DEFAULT_SECONDS} s and SQL reports keep the plugin's own query timeout, exactly as before.
 *
 * <p>When it is set, SQL reports ask the plugin to stop their query {@value #SQL_REPORT_MARGIN_SECONDS} s earlier, so
 * the query never outlives the agent's wait and the plugin's "query timed out" reason still reaches the agent's log.
 */
public final class IiqTimeouts {

    public static final String ENV = "KFAGENT_IIQ_TIMEOUT_SECONDS";
    public static final int DEFAULT_SECONDS = 60;
    public static final int MIN_SECONDS = 20;
    public static final int MAX_SECONDS = 3600;
    public static final int SQL_REPORT_MARGIN_SECONDS = 10;

    private IiqTimeouts() {
    }

    /**
     * The configured timeout in seconds, or {@code null} when {@value #ENV} is not set (environment first, then the
     * system property of the same name, like the agent's other settings).
     *
     * @throws IllegalArgumentException when it is set but is not a whole number of seconds within the allowed range
     */
    public static Integer configured() {
        String raw = System.getenv(ENV);
        if (raw == null || raw.trim().isEmpty()) {
            raw = System.getProperty(ENV);
        }
        return parse(raw);
    }

    static Integer parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(ENV + " '" + raw.trim() + "' is not a whole number of seconds");
        }
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException(ENV + " must be between " + MIN_SECONDS + " and " + MAX_SECONDS
                    + " seconds, but was " + seconds);
        }
        return Integer.valueOf(seconds);
    }

    /** How long to wait for one IdentityIQ call: the configured value, else {@value #DEFAULT_SECONDS} s. */
    public static Duration requestTimeout(Integer configuredSeconds) {
        return Duration.ofSeconds(configuredSeconds == null ? DEFAULT_SECONDS : configuredSeconds.intValue());
    }

    /** The SQL-report query timeout to send to the plugin, or {@code null} to leave the plugin's default. */
    public static Integer sqlReportQueryTimeout(Integer configuredSeconds) {
        return configuredSeconds == null ? null
                : Integer.valueOf(configuredSeconds.intValue() - SQL_REPORT_MARGIN_SECONDS);
    }
}
