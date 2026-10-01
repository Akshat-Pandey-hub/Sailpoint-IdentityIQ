package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeAccessHistoryPage;
import com.keyforge.nativeiiq.model.NativeHistCertificationRow;
import com.keyforge.nativeiiq.model.NativeHistEntitlementCaptureRow;
import com.keyforge.nativeiiq.model.NativeHistIdentityEventRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeAccessHistoryPage} into a plain JSON-friendly structure (only Maps, Lists, Strings,
 * Booleans and Numbers) so it serializes deterministically with IIQ's {@code JsonHelper}. Timestamps are
 * ISO-8601 strings. Pure Java (no SailPoint dependency) but bundled in the plugin jar. The field names here
 * are the wire contract consumed by the standalone loader ({@code com.keyforge.nativeload} repositories) —
 * keep the two in lock-step. Nothing is dropped: the full field-complete Access-History rows are emitted.
 */
public final class NativeAccessHistoryWire {

    private NativeAccessHistoryWire() {
    }

    public static Map<String, Object> errorEnvelope(String entity, int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", entity);
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> entitlementCaptureEnvelope(
            NativeAccessHistoryPage<NativeHistEntitlementCaptureRow> page, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (page != null) {
            for (NativeHistEntitlementCaptureRow r : page.getRows()) {
                rows.add(entitlementRow(r));
            }
        }
        return envelope("HistoricalEntitlementCapture", page, start, limit, rows);
    }

    public static Map<String, Object> identityEventEnvelope(
            NativeAccessHistoryPage<NativeHistIdentityEventRow> page, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (page != null) {
            for (NativeHistIdentityEventRow r : page.getRows()) {
                rows.add(identityEventRow(r));
            }
        }
        return envelope("HistoricalIdentityEvent", page, start, limit, rows);
    }

    public static Map<String, Object> certificationEnvelope(
            NativeAccessHistoryPage<NativeHistCertificationRow> page, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (page != null) {
            for (NativeHistCertificationRow r : page.getRows()) {
                rows.add(certificationRow(r));
            }
        }
        return envelope("HistoricalCertification", page, start, limit, rows);
    }

    private static Map<String, Object> envelope(String entity, NativeAccessHistoryPage<?> page,
                                                int start, int limit, List<Map<String, Object>> rows) {
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", entity);
        env.put("sourceSystem", page == null ? null : page.getSourceSystem());
        env.put("extractionRunId", page == null ? null : page.getExtractionRunId());
        env.put("sourceCount", Integer.valueOf(page == null ? -1 : page.getSourceCount()));
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> entitlementRow(NativeHistEntitlementCaptureRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("entityId", r.getEntityId());
        m.put("entityName", r.getEntityName());
        m.put("identityId", r.getIdentityId());
        m.put("identityName", r.getIdentityName());
        m.put("identityEntitlementId", r.getIdentityEntitlementId());
        m.put("applicationId", r.getApplicationId());
        m.put("applicationName", r.getApplicationName());
        m.put("nativeIdentity", r.getNativeIdentity());
        m.put("instance", r.getInstance());
        m.put("displayValue", r.getDisplayValue());
        m.put("attributeName", r.getAttributeName());
        m.put("attributeValue", r.getAttributeValue());
        m.put("type", r.getType());
        m.put("grantedByRole", Boolean.valueOf(r.isGrantedByRole()));
        m.put("roleId", r.getRoleId());
        m.put("requestItemId", r.getRequestItemId());
        m.put("pendingRequestItemId", r.getPendingRequestItemId());
        m.put("certificationItemId", r.getCertificationItemId());
        m.put("deleted", Boolean.valueOf(r.isDeleted()));
        m.put("effectiveDate", iso(r.getEffectiveDate()));
        m.put("extendedToDate", iso(r.getExtendedToDate()));
        m.put("latest", Boolean.valueOf(r.isLatest()));
        m.put("compressed", Boolean.valueOf(r.isCompressed()));
        m.put("brief", Boolean.valueOf(r.isBrief()));
        m.put("full", Boolean.valueOf(r.isFull()));
        m.put("patch", Boolean.valueOf(r.isPatch()));
        m.put("smartHash", r.getSmartHash());
        m.put("fullHash", r.getFullHash());
        m.put("jsonFormat", r.getJsonFormat());
        m.put("transformType", r.getTransformType());
        m.put("patchDocParent", r.getPatchDocParent());
        m.put("compressedPropertyFlag", r.getCompressedPropertyFlag());
        m.put("compressedPropertyFlagValue", r.getCompressedPropertyFlagValue());
        m.put("compressiblePropertyNames", new ArrayList<String>(r.getCompressiblePropertyNames()));
        m.put("captureJson", r.getCaptureJson());
        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));
        m.put("extendedAttributes", new LinkedHashMap<String, Object>(r.getExtendedAttributes()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        putLineage(m, r.getSrcSystem(), r.getSrcInterface(), r.getSrcObjectType(),
                r.getExtractionRunId(), r.getExtractedAt());
        return m;
    }

    static Map<String, Object> identityEventRow(NativeHistIdentityEventRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("entityId", r.getEntityId());
        m.put("entityName", r.getEntityName());
        m.put("definedEntityName", r.getDefinedEntityName());
        m.put("eventType", r.getEventType());
        m.put("eventCategory", r.getEventCategory());
        m.put("eventSourceType", r.getEventSourceType());
        m.put("eventDate", iso(r.getEventDate()));
        m.put("eventDetailJson", r.getEventDetailJson());
        m.put("prevCaptureId", r.getPrevCaptureId());
        m.put("captureId", r.getCaptureId());
        m.put("auditEventId", r.getAuditEventId());
        m.put("propertyName", r.getPropertyName());
        m.put("oldValue", r.getOldValue());
        m.put("newValue", r.getNewValue());
        m.put("accountId", r.getAccountId());
        m.put("acctAppId", r.getAcctAppId());
        m.put("acctAppName", r.getAcctAppName());
        m.put("acctAppInstance", r.getAcctAppInstance());
        m.put("acctDisplayName", r.getAcctDisplayName());
        m.put("acctNativeId", r.getAcctNativeId());
        m.put("pendingRequestItemId", r.getPendingRequestItemId());
        m.put("requestItemId", r.getRequestItemId());
        m.put("identityRequestId", r.getIdentityRequestId());
        m.put("identityEntitlementId", r.getIdentityEntitlementId());
        m.put("qryProperty1", r.getQryProperty1());
        m.put("qryProperty2", r.getQryProperty2());
        m.put("qryProperty3", r.getQryProperty3());
        m.put("qryProperty4", r.getQryProperty4());
        m.put("qryProperty5", r.getQryProperty5());
        m.put("qryProperty6", r.getQryProperty6());
        m.put("qryProperty7", r.getQryProperty7());
        m.put("qryProperty8", r.getQryProperty8());
        m.put("qryProperty9", r.getQryProperty9());
        m.put("qryProperty10", r.getQryProperty10());
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        putLineage(m, r.getSrcSystem(), r.getSrcInterface(), r.getSrcObjectType(),
                r.getExtractionRunId(), r.getExtractedAt());
        return m;
    }

    static Map<String, Object> certificationRow(NativeHistCertificationRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("certId", r.getCertId());
        m.put("certType", r.getCertType());
        m.put("certName", r.getCertName());
        m.put("certDisplayName", r.getCertDisplayName());
        m.put("finished", iso(r.getFinished()));
        m.put("signed", iso(r.getSigned()));
        m.put("certJson", r.getCertJson());
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        putLineage(m, r.getSrcSystem(), r.getSrcInterface(), r.getSrcObjectType(),
                r.getExtractionRunId(), r.getExtractedAt());
        return m;
    }

    private static void putLineage(Map<String, Object> m, String srcSystem, String srcInterface,
                                   String srcObjectType, String extractionRunId, Instant extractedAt) {
        m.put("srcSystem", srcSystem);
        m.put("srcInterface", srcInterface);
        m.put("srcObjectType", srcObjectType);
        m.put("extractionRunId", extractionRunId);
        m.put("extractedAt", iso(extractedAt));
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
