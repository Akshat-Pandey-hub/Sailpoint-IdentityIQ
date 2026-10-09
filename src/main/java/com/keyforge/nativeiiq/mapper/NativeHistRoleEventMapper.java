package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeHistRoleEventRow;
import com.keyforge.nativeiiq.wire.NativeSerialize;

import sailpoint.object.AuditEvent;
import sailpoint.object.accesshistory.HistoricalRoleEvent;

import java.time.Instant;
import java.util.Date;

/**
 * Maps a native {@code sailpoint.object.accesshistory.HistoricalRoleEvent} into a
 * {@link NativeHistRoleEventRow}. Read-only; getters verified against the 8.4 {@code identityiq.jar}
 * (HistoricalRoleEvent inherits all data getters from {@code HistoricalEvent} and
 * {@code QueryableAccessHistory}). Event enum/category/source are kept as their source {@code name()} — never
 * reinterpreted into KeyForge classifications. The linked AuditEvent is kept only as its id (a reference).
 */
public final class NativeHistRoleEventMapper {

    private NativeHistRoleEventMapper() {
    }

    public static NativeHistRoleEventRow map(HistoricalRoleEvent e,
                                                 String sourceSystem, String extractionRunId) {
        NativeHistRoleEventRow row = new NativeHistRoleEventRow();

        row.setSourceId(e.getId());
        row.setName(e.getName());
        row.setEntityId(e.getEntityId());
        row.setEntityName(e.getEntityName());
        row.setDefinedEntityName(e.getDefinedEntityName());
        row.setEventType(NativeSerialize.enumName(e.getEventType()));
        row.setEventCategory(NativeSerialize.enumName(e.getEventCategory()));
        row.setEventSourceType(NativeSerialize.enumName(e.getEventSourceType()));
        row.setEventDate(toInstant(e.getEventDate()));
        row.setEventDetailJson(e.getEventDetailJson());
        row.setPrevCaptureId(e.getPrevCaptureId());
        row.setCaptureId(e.getCaptureId());
        row.setAuditEventId(auditEventId(e));
        row.setPropertyName(e.getPropertyName());
        row.setOldValue(e.getOldValue());
        row.setNewValue(e.getNewValue());
        row.setAccountId(e.getAccountId());
        row.setAcctAppId(e.getAcctAppId());
        row.setAcctAppName(e.getAcctAppName());
        row.setAcctAppInstance(e.getAcctAppInstance());
        row.setAcctDisplayName(e.getAcctDisplayName());
        row.setAcctNativeId(e.getAcctNativeId());
        row.setPendingRequestItemId(e.getPendingRequestItemId());
        row.setRequestItemId(e.getRequestItemId());
        row.setIdentityRequestId(e.getIdentityRequestId());
        row.setIdentityEntitlementId(e.getIdentityEntitlementId());
        row.setQryProperty1(e.getQryProperty1());
        row.setQryProperty2(e.getQryProperty2());
        row.setQryProperty3(e.getQryProperty3());
        row.setQryProperty4(e.getQryProperty4());
        row.setQryProperty5(e.getQryProperty5());
        row.setQryProperty6(e.getQryProperty6());
        row.setQryProperty7(e.getQryProperty7());
        row.setQryProperty8(e.getQryProperty8());
        row.setQryProperty9(e.getQryProperty9());
        row.setQryProperty10(e.getQryProperty10());
        row.setCreated(toInstant(e.getCreated()));
        row.setModified(toInstant(e.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    /** The linked AuditEvent's id, if any — a reference, never the resolved object. Never throws. */
    private static String auditEventId(HistoricalRoleEvent e) {
        try {
            AuditEvent ae = e.getAuditEvent();
            return ae == null ? null : ae.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
