package com.keyforge.nativeload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.deletion.SoftDeleteSweeper;
import com.keyforge.iiq.parquet.ParquetIds;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Idempotent native Bundle Profile constraints/permissions; only unique source-backed catalog matches are linked. */
public final class NativeRoleEntitlementRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String schema;
    private final String table;
    public enum Outcome { INSERTED, UPDATED }

    public NativeRoleEntitlementRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_role_entitlement";
    }
    public String targetTable() { return table; }

    public void ensure(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            s.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "roleentitlementid uuid PRIMARY KEY, source_role_id text NOT NULL, role_id uuid, role_name text, "
                    + "entitlement_id uuid, entitlement_type text NOT NULL, application_id text, application_name text, "
                    + "profile_ordinal integer, constraint_path text, filter_expression text, filter_value jsonb, "
                    + "filter_operation text, attribute_name text, attribute_value text, permission_target text, "
                    + "permission_rights text, permission_rights_list jsonb, permission_annotation text, "
                    + "source_system text, source_interface text, source_object_type text, src_object_id text, "
                    + "src_natural_key text NOT NULL, extraction_run_id text, extracted_at timestamptz NOT NULL DEFAULT now(), "
                    + "record_hash text, is_deleted boolean NOT NULL DEFAULT false, deleted_at timestamptz)");
        }
    }

    static String id(NativeRoleRelationshipRecord r) {
        return ParquetIds.deterministicUuid("native-role-entitlement|" + r.srcNaturalKey);
    }

    static String hash(NativeRoleRelationshipRecord r, String entitlementId) {
        Map<String, Object> b = new TreeMap<String, Object>();
        b.put("source_role_id", r.sourceBundleId); b.put("role_name", r.roleName);
        b.put("entitlement_id", entitlementId); b.put("entitlement_type", r.entitlementType);
        b.put("application_id", r.applicationId); b.put("application_name", r.application);
        b.put("profile_ordinal", r.profileOrdinal); b.put("constraint_path", r.constraintPath);
        b.put("filter_expression", r.filterExpression); b.put("filter_value", canonical(r.filterValue));
        b.put("filter_operation", r.filterOperation); b.put("attribute_name", r.attributeName);
        b.put("attribute_value", r.attributeValue); b.put("permission_target", r.permissionTarget);
        b.put("permission_rights", r.permissionRights); b.put("permission_rights_list", canonical(r.permissionRightsList));
        b.put("permission_annotation", r.permissionAnnotation);
        return NativeRecordHash.of(b);
    }

    public Outcome upsert(Connection c, NativeRoleRelationshipRecord r) throws SQLException {
        String entitlementId = resolveEntitlement(c, r);
        String sql = "INSERT INTO " + table + " (roleentitlementid,source_role_id,role_id,role_name,entitlement_id,"
                + "entitlement_type,application_id,application_name,profile_ordinal,constraint_path,filter_expression,"
                + "filter_value,filter_operation,attribute_name,attribute_value,permission_target,permission_rights,"
                + "permission_rights_list,permission_annotation,source_system,source_interface,source_object_type,"
                + "src_object_id,src_natural_key,extraction_run_id,record_hash) VALUES (?::uuid,?,?,?,?::uuid,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT(roleentitlementid) DO UPDATE SET source_role_id=excluded.source_role_id,role_id=excluded.role_id,"
                + "role_name=excluded.role_name,entitlement_id=excluded.entitlement_id,entitlement_type=excluded.entitlement_type,"
                + "application_id=excluded.application_id,application_name=excluded.application_name,profile_ordinal=excluded.profile_ordinal,"
                + "constraint_path=excluded.constraint_path,filter_expression=excluded.filter_expression,filter_value=excluded.filter_value,"
                + "filter_operation=excluded.filter_operation,attribute_name=excluded.attribute_name,attribute_value=excluded.attribute_value,"
                + "permission_target=excluded.permission_target,permission_rights=excluded.permission_rights,"
                + "permission_rights_list=excluded.permission_rights_list,permission_annotation=excluded.permission_annotation,"
                + "source_system=excluded.source_system,source_interface=excluded.source_interface,source_object_type=excluded.source_object_type,"
                + "src_object_id=excluded.src_object_id,src_natural_key=excluded.src_natural_key,extraction_run_id=excluded.extraction_run_id,"
                + "record_hash=excluded.record_hash,extracted_at=now(),is_deleted=false,deleted_at=null RETURNING (xmax=0)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1;
            p.setString(i++, id(r)); p.setString(i++, r.sourceBundleId); p.setString(i++, uuid(r.sourceBundleId));
            p.setString(i++, r.roleName); setUuid(p, i++, entitlementId); p.setString(i++, r.entitlementType);
            p.setString(i++, r.applicationId); p.setString(i++, r.application); p.setInt(i++, r.profileOrdinal);
            p.setString(i++, r.constraintPath); p.setString(i++, r.filterExpression); p.setString(i++, json(r.filterValue));
            p.setString(i++, r.filterOperation); p.setString(i++, r.attributeName); p.setString(i++, r.attributeValue);
            p.setString(i++, r.permissionTarget); p.setString(i++, r.permissionRights);
            p.setString(i++, json(r.permissionRightsList == null ? java.util.Collections.emptyList() : r.permissionRightsList));
            p.setString(i++, r.permissionAnnotation); p.setString(i++, r.sourceSystem);
            p.setString(i++, "native_iiq_java_api"); p.setString(i++, "sailpoint.object.Bundle");
            p.setString(i++, r.sourceBundleId); p.setString(i++, r.srcNaturalKey); p.setString(i++, r.extractionRunId);
            p.setString(i, hash(r, entitlementId));
            try (ResultSet x = p.executeQuery()) { return x.next() && x.getBoolean(1) ? Outcome.INSERTED : Outcome.UPDATED; }
        }
    }

    /** Do not guess: only an exact EQ filter uniquely matching native kf_entitlement can link by entitlement_id. */
    private String resolveEntitlement(Connection c, NativeRoleRelationshipRecord r) throws SQLException {
        if (!"PROFILE_CONSTRAINT".equals(r.entitlementType) || !"EQ".equalsIgnoreCase(r.filterOperation)
                || r.application == null || r.attributeName == null || r.attributeValue == null) return null;
        String sql = "SELECT entitlementid::text FROM " + schema
                + ".kf_entitlement WHERE application_name=? AND attribute=? AND value=? ORDER BY entitlementid LIMIT 2";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, r.application); p.setString(2, r.attributeName); p.setString(3, r.attributeValue);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                String id = rs.getString(1);
                return rs.next() ? null : id;
            }
        }
    }

    private static String canonical(String raw) {
        String value = ParquetIds.canonicalUuid(raw);
        return value == null ? ParquetIds.deterministicUuid("native-role|" + raw) : value;
    }
    private static String uuid(String raw) { return canonical(raw); }
    private static void setUuid(PreparedStatement p, int index, String value) throws SQLException {
        if (value == null) p.setNull(index, Types.OTHER); else p.setString(index, value);
    }
    private static String json(Object value) throws SQLException {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new SQLException("Could not serialize native Bundle profile value", e); }
    }
    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> sorted = new TreeMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) sorted.put(String.valueOf(entry.getKey()), canonical(entry.getValue()));
            return sorted;
        }
        if (value instanceof java.util.List<?>) {
            java.util.List<Object> out = new java.util.ArrayList<Object>();
            for (Object item : (java.util.List<?>) value) out.add(canonical(item));
            return out;
        }
        return value;
    }
    public SoftDeleteSweeper.SweepResult sweep(Connection c, Collection<String> ids) throws SQLException {
        return new SoftDeleteSweeper(schema).sweep(c, "kf_role_entitlement", "roleentitlementid", ids);
    }
}
