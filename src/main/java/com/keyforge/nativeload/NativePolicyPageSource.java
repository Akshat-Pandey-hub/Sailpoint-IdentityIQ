package com.keyforge.nativeload;

/** Transport seam for the native Policy payload. Production impl: {@link NativePolicyClient}. */
public interface NativePolicyPageSource {

    String fetchPage(int start, int limit);
}
