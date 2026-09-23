package com.keyforge.nativeload;

import java.sql.SQLException;

/** Persistence seam for native ProvisioningTransaction and derived items. */
public interface NativeProvisioningTxnSink {

    void ensure() throws SQLException;

    NativeProvisioningTxnRepository.UpsertOutcome upsertTxn(NativeProvisioningTxnRecord record) throws SQLException;

    NativeProvisioningItemRepository.UpsertOutcome upsertItem(NativeProvisioningItemRecord record) throws SQLException;

}
