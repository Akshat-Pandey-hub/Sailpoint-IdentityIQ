package com.keyforge.nativeload;

/** Transport seam for the native policy-constraint payload. Production impl: {@link NativePolicyConstraintClient}. */
public interface NativePolicyConstraintPageSource {

    String fetchPage(int start, int limit);
}
