package com.keyforge.nativeload;

/** Transport seam for the native ProvisioningTransaction payload. Impl: NativeProvisioningTxnClient. */
public interface NativeProvisioningTxnPageSource {

    String fetchPage(int start, int limit);
}
