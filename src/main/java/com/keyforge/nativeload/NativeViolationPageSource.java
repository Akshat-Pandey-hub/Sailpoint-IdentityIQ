package com.keyforge.nativeload;

/** Transport seam for the native PolicyViolation payload. Production impl: {@link NativeViolationClient}. */
public interface NativeViolationPageSource {

    String fetchPage(int start, int limit);
}
