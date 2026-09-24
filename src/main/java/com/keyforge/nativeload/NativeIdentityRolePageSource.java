package com.keyforge.nativeload;

/** Transport seam for the native identity-role payload (a window of Identities). */
public interface NativeIdentityRolePageSource {

    String fetchPage(int start, int limit);
}
