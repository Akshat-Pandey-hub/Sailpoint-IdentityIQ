package com.keyforge.nativeiiq.model;

import java.util.ArrayList;
import java.util.List;

/** Page over Bundle objects, expanded into profile-entitlement evidence and typed hierarchy edges. */
public final class NativeRoleRelationshipExtractionResult {
    private int sourceCount, returnedBundles;
    private final List<NativeRoleEntitlementRow> roleEntitlements = new ArrayList<NativeRoleEntitlementRow>();
    private final List<NativeRoleHierarchyRow> roleHierarchy = new ArrayList<NativeRoleHierarchyRow>();
    public int getSourceCount(){return sourceCount;} public void setSourceCount(int v){sourceCount=v;}
    public int getReturnedBundles(){return returnedBundles;} public void setReturnedBundles(int v){returnedBundles=v;}
    public List<NativeRoleEntitlementRow> getRoleEntitlements(){return roleEntitlements;}
    public List<NativeRoleHierarchyRow> getRoleHierarchy(){return roleHierarchy;}
}
