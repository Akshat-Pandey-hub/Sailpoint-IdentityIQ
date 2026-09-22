package com.keyforge.iiq.client;

import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.raw.RawArchive;

import java.util.Map;

/**
 * A {@link IiqSessionClient} that tees every response body (GET / JSON-POST / form-POST) into the
 * immutable RAW zone before returning it unchanged. All session/CSRF behavior is inherited from
 * {@link IiqSessionClient}; only the raw response bodies of the public body-returning methods are
 * additionally captured. A RAW persistence failure propagates for a clean extraction failure.
 */
public final class RawCapturingSessionClient extends IiqSessionClient {

    private final RawArchive archive;
    private final String srcObjectType;
    private final String srcInterface;

    public RawCapturingSessionClient(AppConfig config, boolean debug, RawArchive archive,
                                     String srcObjectType, String srcInterface) {
        super(config, debug);
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

    @Override
    public String postJson(String path, String jsonBody) {
        String body = super.postJson(path, jsonBody);
        archive.capture(srcObjectType, srcInterface, path, body);
        return body;
    }

    @Override
    public String postFormForJson(String path, Map<String, String> formFields) {
        String body = super.postFormForJson(path, formFields);
        archive.capture(srcObjectType, srcInterface, path, body);
        return body;
    }

    @Override
    public String postForm(String path, Map<String, String> formFields) {
        String body = super.postForm(path, formFields);
        archive.capture(srcObjectType, srcInterface, path, body);
        return body;
    }
}
