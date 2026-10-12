package com.keyforge.nativeload;

/**
 * Request-scoped completeness evidence for KF Agent REST responses. A REST service whose internal full scan passed its
 * import service's complete-scan guard (the scan covered exactly the IIQ {@code countObjects} total; otherwise the
 * import throws "Incomplete … scan" and the request fails) publishes that verified count here; the KF Agent server
 * emits it as the {@value #HEADER} response header on a 200 response and clears it after every request.
 *
 * <p>The header means: "this response was built from ONE full internal scan whose completeness was verified against
 * an IIQ source count of N". It is absent whenever no count was verified (the plugin reported none). The response body
 * is unchanged, so clients that ignore the header are unaffected.
 */
public final class KfAgentScanEvidence {

    /** Response header carrying the verified source count. */
    public static final String HEADER = "X-KF-Verified-Source-Count";

    private static final ThreadLocal<Integer> VERIFIED = new ThreadLocal<>();

    private KfAgentScanEvidence() {
    }

    /**
     * Records a scan's verified count. {@code sourceCount < 0} (no count reported by the plugin) or a scanned total that
     * differs from it records nothing — such a scan is not verified complete.
     */
    public static void verified(int sourceCount, int scanned) {
        if (sourceCount >= 0 && scanned == sourceCount) {
            VERIFIED.set(sourceCount);
        }
    }

    /** The verified count for the current request, or {@code null}. */
    public static Integer current() {
        return VERIFIED.get();
    }

    /** Forgets any recorded evidence (called at the start and end of every request). */
    public static void clear() {
        VERIFIED.remove();
    }
}
