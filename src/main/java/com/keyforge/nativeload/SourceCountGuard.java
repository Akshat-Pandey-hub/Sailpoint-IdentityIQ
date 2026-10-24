package com.keyforge.nativeload;

import java.util.function.ToIntFunction;

/**
 * Fails a multi-page KF Agent read when the plugin-reported source count changes between its calls: the offset
 * window may have shifted, so rows could be skipped or repeated. The same rule the import services apply to a full
 * scan, applied to the bounded reads of {@link KfAgentPagedReader}. A page without a count ({@code -1}) is ignored.
 */
final class SourceCountGuard {

    private final String entity;
    private final ToIntFunction<String> sourceCount;
    private int baseline = -1;

    SourceCountGuard(String entity, ToIntFunction<String> sourceCount) {
        this.entity = entity;
        this.sourceCount = sourceCount;
    }

    /** Returns {@code json} unchanged after checking its source count against the first page's. */
    String check(String json) {
        checkCount(sourceCount.applyAsInt(json));
        return json;
    }

    /** Checks an already-parsed source count against the first page's count. */
    void checkCount(int count) {
        if (count >= 0) {
            if (baseline >= 0 && baseline != count) {
                throw new NativeImportException(entity + " source count changed during paginated extraction: "
                        + baseline + " -> " + count);
            }
            baseline = count;
        }
    }
}
