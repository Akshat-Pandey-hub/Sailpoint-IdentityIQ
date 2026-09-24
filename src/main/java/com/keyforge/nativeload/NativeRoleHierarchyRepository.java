package com.keyforge.nativeload;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.deletion.SoftDeleteSweeper;
import com.keyforge.iiq.parquet.ParquetIds;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Idempotent Bundle-to-Bundle edges; preserves inheritance, requirement, and permit as distinct types. */
public final class NativeRoleHierarchyRepository {
    private final String schema;
    private final String table;
    public enum Outcome { INSERTED, UPDATED }
    public NativeRoleHierarchyRepository(String schema) {
        this.schema=SchemaName.validate(schema); this.table=this.schema+".kf_role_hierarchy";
    }
    public String targetTable() { return table; }
    public void ensure(Connection c) throws SQLException {
        try (Statement s=c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS "+schema);
            s.execute("CREATE TABLE IF NOT EXISTS "+table+" (rolehierarchyid uuid PRIMARY KEY, source_role_id text NOT NULL, role_id uuid, role_name text, "
                    +"related_role_source_id text NOT NULL, related_role_id uuid, related_role_name text, relationship_type text NOT NULL, "
                    +"source_system text, source_interface text, source_object_type text, src_object_id text, src_natural_key text NOT NULL, "
                    +"extraction_run_id text, extracted_at timestamptz NOT NULL DEFAULT now(), record_hash text, "
                    +"is_deleted boolean NOT NULL DEFAULT false, deleted_at timestamptz)");
        }
    }
    static String id(NativeRoleHierarchyRecord r) {
        return ParquetIds.deterministicUuid("native-role-hierarchy|"+r.srcNaturalKey);
    }
    static String hash(NativeRoleHierarchyRecord r) {
        Map<String,Object> b=new LinkedHashMap<String,Object>();
        b.put("source_role_id",r.sourceRoleId); b.put("source_role_name",r.sourceRoleName);
        b.put("related_role_id",r.relatedRoleId); b.put("related_role_name",r.relatedRoleName);
        b.put("relationship_type",r.relationshipType);
        return NativeRecordHash.of(b);
    }
    public Outcome upsert(Connection c,NativeRoleHierarchyRecord r) throws SQLException {
        String sql="INSERT INTO "+table+" (rolehierarchyid,source_role_id,role_id,role_name,related_role_source_id,related_role_id,related_role_name,relationship_type,source_system,source_interface,source_object_type,src_object_id,src_natural_key,extraction_run_id,record_hash) "
                +"VALUES (?::uuid,?,?,?, ?,?::uuid,?,?,?,?,?,?,?,?,?) ON CONFLICT(rolehierarchyid) DO UPDATE SET "
                +"source_role_id=excluded.source_role_id,role_id=excluded.role_id,role_name=excluded.role_name,related_role_source_id=excluded.related_role_source_id,related_role_id=excluded.related_role_id,related_role_name=excluded.related_role_name,relationship_type=excluded.relationship_type,source_system=excluded.source_system,source_interface=excluded.source_interface,source_object_type=excluded.source_object_type,src_object_id=excluded.src_object_id,src_natural_key=excluded.src_natural_key,extraction_run_id=excluded.extraction_run_id,record_hash=excluded.record_hash,extracted_at=now(),is_deleted=false,deleted_at=null RETURNING (xmax=0)";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            int i=1; p.setString(i++,id(r)); p.setString(i++,r.sourceRoleId); p.setString(i++,uuid(r.sourceRoleId));
            p.setString(i++,r.sourceRoleName); p.setString(i++,r.relatedRoleId); p.setString(i++,uuid(r.relatedRoleId));
            p.setString(i++,r.relatedRoleName); p.setString(i++,r.relationshipType); p.setString(i++,r.sourceSystem);
            p.setString(i++,"native_iiq_java_api"); p.setString(i++,"sailpoint.object.Bundle"); p.setString(i++,r.sourceRoleId);
            p.setString(i++,r.srcNaturalKey); p.setString(i++,r.extractionRunId); p.setString(i,hash(r));
            try(ResultSet rs=p.executeQuery()){return rs.next()&&rs.getBoolean(1)?Outcome.INSERTED:Outcome.UPDATED;}
        }
    }
    private static String uuid(String raw) {
        String v=ParquetIds.canonicalUuid(raw); return v==null?ParquetIds.deterministicUuid("native-role|"+raw):v;
    }
    public SoftDeleteSweeper.SweepResult sweep(Connection c,Collection<String> ids)throws SQLException {
        return new SoftDeleteSweeper(schema).sweep(c,"kf_role_hierarchy","rolehierarchyid",ids);
    }
}
