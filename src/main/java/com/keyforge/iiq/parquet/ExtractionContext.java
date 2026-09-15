package com.keyforge.iiq.parquet;

import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.iiq.config.AppConfig;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-execution context for a Parquet extraction run: the IIQ config, this run's
 * {@code extraction_run_id} and {@code extracted_at}, memoized IIQ clients, and a cache so datasets
 * that share a source (e.g. access request/item/approval, or audit events reused by the event-link
 * derivation) extract it once. PostgreSQL-independent.
 */
public final class ExtractionContext {

    private final AppConfig config;
    private final String runId;
    private final Instant extractedAt;
    private final boolean debugHttp;
    private final Map<String, Object> cache = new HashMap<>();

    private IiqApiClient api;
    private IiqSessionClient session;

    public ExtractionContext(AppConfig config, String runId, Instant extractedAt, boolean debugHttp) {
        this.config = config;
        this.runId = runId;
        this.extractedAt = extractedAt;
        this.debugHttp = debugHttp;
    }

    public AppConfig config() {
        return config;
    }

    public String runId() {
        return runId;
    }

    public Instant extractedAt() {
        return extractedAt;
    }

    /** Basic-auth SCIM client (memoized). */
    public IiqApiClient api() {
        if (api == null) {
            api = new IiqApiClient(config);
        }
        return api;
    }

    /** Session+CSRF client for classic UI/REST (memoized). */
    public IiqSessionClient session() {
        if (session == null) {
            session = new IiqSessionClient(config, debugHttp);
        }
        return session;
    }

    /** Memoizes a shared source extraction under a key. */
    @SuppressWarnings("unchecked")
    public <T> T memo(String key, Supplier<T> supplier) {
        return (T) cache.computeIfAbsent(key, k -> supplier.get());
    }
}
