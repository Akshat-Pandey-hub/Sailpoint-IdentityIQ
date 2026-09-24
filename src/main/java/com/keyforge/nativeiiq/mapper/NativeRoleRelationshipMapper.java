package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeRoleEntitlementRow;
import com.keyforge.nativeiiq.model.NativeRoleHierarchyRow;
import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;
import com.keyforge.nativeiiq.wire.JsonSafe;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Maps only Bundle Profile and Bundle relationship declarations; does not resolve names or execute rules. */
public final class NativeRoleRelationshipMapper {
    private NativeRoleRelationshipMapper() { }

    public static void map(Bundle b, NativeRoleRelationshipExtractionResult out, String system, String runId) {
        if (b == null || b.getId() == null) return;
        List<Profile> profiles=b.getProfiles();
        if(profiles!=null) for(Profile p:profiles) if(p!=null) mapProfile(b,p,out,system,runId);
        addHierarchy(b,b.getInheritance(),"INHERITANCE",out,system,runId);
        addHierarchy(b,b.getRequirements(),"REQUIREMENT",out,system,runId);
        addHierarchy(b,b.getPermits(),"PERMIT",out,system,runId);
    }

    private static void mapProfile(Bundle b, Profile p, NativeRoleRelationshipExtractionResult out,String system,String runId){
        Application app=p.getApplication();
        String appId=app==null?null:app.getId(), appName=app==null?null:app.getName();
        List<Filter> constraints=p.getConstraints();
        if(constraints!=null){
            for(int i=0;i<constraints.size();i++) {
                Filter root=constraints.get(i);
                flatten(b,p,appId,appName,root,Integer.toString(i),root==null?null:root.getExpression(),out,system,runId,0);
            }
        }
        List<Permission> permissions=p.getPermissions();
        if(permissions!=null) for(int i=0;i<permissions.size();i++){
            Permission permission=permissions.get(i); if(permission==null)continue;
            NativeRoleEntitlementRow r=base(b,p,appId,appName,system,runId);
            r.setEntitlementType("PROFILE_PERMISSION"); r.setConstraintPath("permission:"+i);
            r.setPermissionTarget(permission.getTarget()); r.setPermissionRights(permission.getRights());
            r.setPermissionAnnotation(permission.getAnnotation());
            List<String> rights=permission.getRightsList(); if(rights!=null)r.setPermissionRightsList(new ArrayList<String>(rights));
            r.setSrcNaturalKey(naturalKey(r)); out.getRoleEntitlements().add(r);
        }
    }

    private static void flatten(Bundle b,Profile p,String appId,String appName,Filter f,String path,String rootExpression,
                                NativeRoleRelationshipExtractionResult out,String system,String runId,int depth){
        if(f==null||depth>20)return;
        if(f instanceof Filter.LeafFilter){
            Filter.LeafFilter leaf=(Filter.LeafFilter)f;
            NativeRoleEntitlementRow r=base(b,p,appId,appName,system,runId);
            r.setEntitlementType("PROFILE_CONSTRAINT"); r.setConstraintPath(path); r.setFilterExpression(rootExpression);
            r.setAttributeName(leaf.getProperty());
            r.setFilterOperation(leaf.getOperation()==null?null:leaf.getOperation().toString());
            Object value=leaf.getValue(); Object safe=JsonSafe.toJsonSafe(value);
            r.setFilterValue(safe);
            if(safe instanceof String||safe instanceof Number||safe instanceof Boolean)r.setAttributeValue(String.valueOf(safe));
            r.setSrcNaturalKey(naturalKey(r)); out.getRoleEntitlements().add(r);
        } else if(f instanceof Filter.CompositeFilter){
            Filter.CompositeFilter composite=(Filter.CompositeFilter)f;
            List<Filter> children=composite.getChildren(); if(children!=null)for(int i=0;i<children.size();i++)
                flatten(b,p,appId,appName,children.get(i),path+"."+i,rootExpression,out,system,runId,depth+1);
        }
    }

    private static NativeRoleEntitlementRow base(Bundle b,Profile p,String appId,String appName,String system,String runId){
        NativeRoleEntitlementRow r=new NativeRoleEntitlementRow(); r.setSourceBundleId(b.getId());
        r.setRoleId(b.getId());r.setRoleName(b.getName());r.setApplicationId(appId);r.setApplication(appName);
        r.setProfileOrdinal(p.getProfileOrdinal());r.setSourceSystem(system);r.setExtractionRunId(runId);
        r.setExtractedAt(Instant.now().toString());return r;
    }

    private static void addHierarchy(Bundle source,List<Bundle> related,String type,NativeRoleRelationshipExtractionResult out,String system,String runId){
        if(related==null)return;
        for(Bundle target:related){if(target==null||target.getId()==null)continue;NativeRoleHierarchyRow r=new NativeRoleHierarchyRow();
            r.setSourceRoleId(source.getId());r.setSourceRoleName(source.getName());r.setRelatedRoleId(target.getId());
            r.setRelatedRoleName(target.getName());r.setRelationshipType(type);r.setSourceSystem(system);r.setExtractionRunId(runId);
            r.setSrcNaturalKey(part(source.getId())+part(type)+part(target.getId()));r.setExtractedAt(Instant.now().toString());out.getRoleHierarchy().add(r);}
    }
    private static String naturalKey(NativeRoleEntitlementRow r){
        return part(r.getSourceBundleId())+part(Integer.toString(r.getProfileOrdinal()))+part(r.getEntitlementType())
                +part(r.getConstraintPath())+part(r.getApplicationId())+part(r.getAttributeName())
                +part(r.getFilterOperation())+part(canonical(r.getFilterValue()))
                +part(r.getPermissionTarget())+part(r.getPermissionRights())
                +part(canonical(r.getPermissionRightsList()))+part(r.getPermissionAnnotation());
    }
    private static String part(String value){return value==null?"-1:":value.length()+":"+value;}
    private static String canonical(Object value){
        if(value==null)return "null";
        if(value instanceof java.util.Map<?,?>){
            java.util.TreeMap<String,Object> sorted=new java.util.TreeMap<String,Object>();
            for(java.util.Map.Entry<?,?> e:((java.util.Map<?,?>)value).entrySet())sorted.put(String.valueOf(e.getKey()),e.getValue());
            StringBuilder out=new StringBuilder("{");for(java.util.Map.Entry<String,Object>e:sorted.entrySet())out.append(part(e.getKey())).append(part(canonical(e.getValue())));return out.append('}').toString();
        }
        if(value instanceof java.util.List<?>){StringBuilder out=new StringBuilder("[");for(Object item:(java.util.List<?>)value)out.append(part(canonical(item)));return out.append(']').toString();}
        return value.getClass().getName()+":"+String.valueOf(value);
    }
}
