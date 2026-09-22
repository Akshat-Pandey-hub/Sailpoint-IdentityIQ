package com.keyforge.iiq.client;

import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.raw.RawArchive;
import com.keyforge.iiq.raw.RawConfig;
import com.keyforge.iiq.runledger.RunLedger;

import java.util.Optional;

/**
 * Single construction boundary for IIQ clients. When the RAW zone is enabled (i.e. {@code RAW_OUT_DIR}
 * is set), returns a RAW-capturing decorator; otherwise returns the plain client, so behavior is
 * identical to before this class existed when RAW is disabled. The {@code extraction_run_id} for the
 * RAW manifest comes from {@link RunLedger#currentRunId()} (set inside every {@code -db} command).
 */
public final class IiqClients {

    private IiqClients() {
    }

    /** A Basic-auth SCIM/REST client, RAW-capturing when enabled. */
    public static IiqApiClient basic(AppConfig config, String srcObjectType, String srcInterface) {
        Optional<RawConfig> raw = RawConfig.loadIfEnabled();
        if (raw.isEmpty()) {
            return new IiqApiClient(config);
        }
        return new RawCapturingClient(config, new RawArchive(raw.get(), RunLedger.currentRunId()),
                srcObjectType, srcInterface);
    }

    /** A session (cookie + CSRF) client, RAW-capturing when enabled. */
    public static IiqSessionClient session(AppConfig config, boolean debug,
                                           String srcObjectType, String srcInterface) {
        Optional<RawConfig> raw = RawConfig.loadIfEnabled();
        if (raw.isEmpty()) {
            return new IiqSessionClient(config, debug);
        }
        return new RawCapturingSessionClient(config, debug, new RawArchive(raw.get(), RunLedger.currentRunId()),
                srcObjectType, srcInterface);
    }
}
