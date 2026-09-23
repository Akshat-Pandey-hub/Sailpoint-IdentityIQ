package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeProvisioningItemRow;
import com.keyforge.nativeiiq.model.NativeProvisioningTxnRow;

import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ProvisioningTransaction;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Maps a native {@code sailpoint.object.ProvisioningTransaction} into a {@link NativeProvisioningTxnRow},
 * deriving its provisioning items from the embedded {@code AccountRequest} (the {@code "request"} attribute).
 * Read-only; only getters verified against the 8.4 {@code identityiq.jar}.
 *
 * <p><b>Security:</b> only the specific known attribute keys are read (never the whole {@code getAttributes()}
 * map), and any {@code AttributeRequest} value whose name looks like a secret (password/secret/token/…) is
 * redacted to {@code "<redacted>"} before it is stored. Nothing is inferred — a native {@code null} stays null.
 */
public final class NativeProvisioningTxnMapper {

    private static final String REDACTED = "<redacted>";

    private NativeProvisioningTxnMapper() {
    }

    public static NativeProvisioningTxnRow map(ProvisioningTransaction pt, String sourceSystem, String extractionRunId) {
        NativeProvisioningTxnRow row = new NativeProvisioningTxnRow();

        row.setSourceId(pt.getId());
        row.setName(pt.getName());
        row.setOperation(pt.getOperation());
        row.setType(enumName(pt.getType()));
        row.setStatus(enumName(pt.getStatus()));
        row.setSource(pt.getSource());
        row.setIntegration(pt.getIntegration());
        row.setForced(Boolean.valueOf(pt.isForced()));

        row.setIdentityName(pt.getIdentityName());
        row.setIdentityDisplayName(pt.getIdentityDisplayName());
        row.setApplicationName(pt.getApplicationName());
        row.setNativeIdentity(pt.getNativeIdentity());
        row.setAccountDisplayName(pt.getAccountDisplayName());
        row.setCertificationId(pt.getCertificationId());

        Attributes<String, Object> attrs = pt.getAttributes();
        if (attrs != null) {
            row.setCertificationName(str(attrs.get(ProvisioningTransaction.ATT_CERT_NAME)));
            row.setAccessRequestId(str(attrs.get(ProvisioningTransaction.ATT_ACCESS_REQUEST_ID)));
            row.setWaitWorkItemId(str(attrs.get(ProvisioningTransaction.ATT_WAIT_WORK_ITEM_ID)));
            row.setManualWorkItemId(str(attrs.get(ProvisioningTransaction.ATT_MANUAL_WORK_ITEM)));
            row.setTicketId(str(attrs.get(ProvisioningTransaction.ATT_TICKET_ID)));
            row.setRetryRequestId(str(attrs.get(ProvisioningTransaction.ATT_RETRY_REQUEST_ID)));
            row.setLastRetry(toInstant(attrs.getDate(ProvisioningTransaction.ATT_LAST_RETRY)));
            if (attrs.get(ProvisioningTransaction.ATT_RETRY_COUNT) != null) {
                row.setRetryCount(Integer.valueOf(attrs.getInt(ProvisioningTransaction.ATT_RETRY_COUNT)));
            }
            if (attrs.get(ProvisioningTransaction.ATT_TIMED_OUT) != null) {
                row.setTimedOut(Boolean.valueOf(attrs.getBoolean(ProvisioningTransaction.ATT_TIMED_OUT)));
            }
            if (attrs.get(ProvisioningTransaction.ATT_FILTERED) != null) {
                row.setFiltered(Boolean.valueOf(attrs.getBoolean(ProvisioningTransaction.ATT_FILTERED)));
            }
            mapPlanResult(attrs.get(ProvisioningTransaction.ATT_PLAN_RESULT), row);
            mapRequest(attrs.get(ProvisioningTransaction.ATT_REQUEST), row);
        }

        Identity owner = pt.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }
        row.setCreated(toInstant(pt.getCreated()));
        row.setModified(toInstant(pt.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static void mapPlanResult(Object planResult, NativeProvisioningTxnRow row) {
        if (!(planResult instanceof ProvisioningResult)) {
            return;
        }
        ProvisioningResult pr = (ProvisioningResult) planResult;
        row.setPlanResultStatus(pr.getStatus());
        row.setPlanResultRequestId(pr.getRequestID());
        List<?> errors = pr.getErrors();
        if (errors != null) {
            for (Object m : errors) {
                if (m != null) {
                    row.getPlanResultErrors().add(messageText(m));
                }
            }
        }
    }

    private static void mapRequest(Object request, NativeProvisioningTxnRow row) {
        if (!(request instanceof ProvisioningPlan.AccountRequest)) {
            return;
        }
        ProvisioningPlan.AccountRequest ar = (ProvisioningPlan.AccountRequest) request;
        String accountOp = enumName(ar.getOperation());
        row.setAccountRequestOperation(accountOp);
        row.setRequestId(ar.getRequestID());

        int index = 0;
        List<ProvisioningPlan.AttributeRequest> attrReqs = ar.getAttributeRequests();
        if (attrReqs != null) {
            for (ProvisioningPlan.AttributeRequest a : attrReqs) {
                if (a == null) {
                    continue;
                }
                NativeProvisioningItemRow item = baseItem(ar, accountOp, row, index++);
                item.setItemType("ATTRIBUTE");
                item.setOperation(enumName(a.getOp()));
                item.setName(a.getName());
                boolean secret = a.isSecret() || ProvisioningPlan.isSecret(a.getName()) || secretName(a.getName());
                setSafeValue(item, secret, a.getName(), secret ? null : a.getValue());
                item.setAssignmentId(a.getAssignmentId());
                item.setAssignment(Boolean.valueOf(a.isAssignment()));
                row.getItems().add(item);
            }
        }
        List<ProvisioningPlan.PermissionRequest> permReqs = ar.getPermissionRequests();
        if (permReqs != null) {
            for (ProvisioningPlan.PermissionRequest p : permReqs) {
                if (p == null) {
                    continue;
                }
                NativeProvisioningItemRow item = baseItem(ar, accountOp, row, index++);
                item.setItemType("PERMISSION");
                item.setOperation(enumName(p.getOp()));
                item.setName(p.getTarget());
                item.setPermissionTarget(p.getTarget());
                boolean secret = p.isSecret() || ProvisioningPlan.isSecret(p.getTarget())
                        || secretName(p.getTarget());
                if (secret) {
                    setSafeValue(item, true, p.getTarget(), null);
                    item.setPermissionRights(REDACTED);
                } else {
                    Object rights = p.getRightsList() == null || p.getRightsList().isEmpty()
                            ? p.getRights() : p.getRightsList();
                    setSafeValue(item, false, p.getTarget(), rights);
                    item.setPermissionRights(p.getRights());
                }
                item.setAssignmentId(p.getAssignmentId());
                item.setAssignment(Boolean.valueOf(p.isAssignment()));
                row.getItems().add(item);
            }
        }
    }

    /** Redacts before touching a source value; unsupported runtime objects never cross the plugin boundary. */
    private static void setSafeValue(NativeProvisioningItemRow item, boolean secret, String name, Object value) {
        Object safe = safeRequestValue(value, secret, name);
        item.setValue(safe instanceof String || safe instanceof Number || safe instanceof Boolean
                ? String.valueOf(safe) : null);
        item.setValueJson(safe);
    }

    private static Object safeValue(Object value, int depth) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (depth >= 12) {
            return null;
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().toString();
        }
        if (value instanceof java.util.Map<?, ?>) {
            java.util.Map<String, Object> out = new java.util.TreeMap<String, Object>();
            for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) value).entrySet()) {
                if (e.getKey() != null) {
                    String key = String.valueOf(e.getKey());
                    out.put(key, secretName(key)
                            ? REDACTED : safeValue(e.getValue(), depth + 1));
                }
            }
            return out;
        }
        if (value instanceof java.util.Collection<?>) {
            java.util.List<Object> out = new java.util.ArrayList<Object>();
            for (Object element : (java.util.Collection<?>) value) {
                out.add(safeValue(element, depth + 1));
            }
            return out;
        }
        if (value instanceof Object[]) {
            java.util.List<Object> out = new java.util.ArrayList<Object>();
            for (Object element : (Object[]) value) {
                out.add(safeValue(element, depth + 1));
            }
            return out;
        }
        return null;
    }

    private static NativeProvisioningItemRow baseItem(ProvisioningPlan.AccountRequest ar, String accountOp,
                                                      NativeProvisioningTxnRow txn, int index) {
        NativeProvisioningItemRow item = new NativeProvisioningItemRow();
        item.setTxnSourceId(txn.getSourceId());
        item.setIdentityName(txn.getIdentityName());
        item.setApplicationName(ar.getApplicationName());
        item.setNativeIdentity(ar.getNativeIdentity());
        item.setInstance(ar.getInstance());
        item.setAccountOperation(accountOp);
        item.setRequestId(ar.getRequestID());
        item.setItemIndex(index);
        return item;
    }

    /** Package-private for unit testing the redaction rule without the IIQ runtime. */
    static boolean secretName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("password") || n.contains("passwd") || n.contains("secret")
                || n.contains("token") || n.contains("credential") || n.equals("key") || n.endsWith("key")
                || n.contains("currentpassword") || n.contains("newpassword");
    }

    private static String messageText(Object m) {
        try {
            // Localized message text can interpolate request values. Persist only the stable message key.
            // Message implements openconnector.OpenMessagePart, a compile-time dependency absent from
            // identityiq.jar. Resolve the verified getKey() method reflectively to keep this plugin
            // build independent of that optional API jar.
            Object keyValue = m.getClass().getMethod("getKey").invoke(m);
            String key = keyValue == null ? null : String.valueOf(keyValue);
            return key == null ? "<message unavailable>" : key;
        } catch (Throwable t) {
            return "<message unavailable>";
        }
    }

    static Object safeRequestValue(Object value, boolean sourceSecret, String name) {
        if (sourceSecret || secretName(name)) {
            return REDACTED;
        }
        return safeValue(value, 0);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
