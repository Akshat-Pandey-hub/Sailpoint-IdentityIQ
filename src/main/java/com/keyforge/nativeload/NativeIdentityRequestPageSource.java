package com.keyforge.nativeload;

/** Transport seam for the native IdentityRequest payload. Impl: NativeIdentityRequestClient. */
public interface NativeIdentityRequestPageSource {

    String fetchPage(int start, int limit);
}
