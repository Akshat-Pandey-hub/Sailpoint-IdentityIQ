package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeSyslogEventExtractionResult;
import com.keyforge.nativeiiq.source.NativeSyslogEventExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) {@code SyslogEvent} extraction service. STRICTLY READ-ONLY. */
public final class NativeSyslogEventExtractionService {

    private final String sourceSystem;

    public NativeSyslogEventExtractionService() {
        this("IdentityIQ");
    }

    public NativeSyslogEventExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeSyslogEventExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeSyslogEventExtractor(context, config).extract(Math.max(0, start), limit);
    }
}
