package com.keyforge.iiq.client;

import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.raw.RawArchive;

import java.util.Map;

/**
 * A {@link IiqApiClient} that tees every response body into the immutable RAW zone before returning it
 * unchanged to the caller. Endpoint-agnostic like its parent; the object-type/interface context is
 * supplied at construction. The real HTTP call and all parsing are inherited unchanged — only the raw
 * body is additionally captured. If RAW persistence fails, the exception propagates so the extraction
 * fails cleanly rather than proceeding as if the payload were archived.
 */
public final class RawCapturingClient extends IiqApiClient {

    private final RawArchive archive;
    private final String srcObjectType;
    private final String srcInterface;

    public RawCapturingClient(AppConfig config, RawArchive archive, String srcObjectType, String srcInterface) {
        super(config);
        this.archive = archive;
        this.srcObjectType = srcObjectType;
        this.srcInterface = srcInterface;
    }

    @Override
    public String get(String path, Map<String, String> queryParams) {
        String body = super.get(path, queryParams);
        archive.capture(srcObjectType, srcInterface, path, body);
        return body;
    }
}
