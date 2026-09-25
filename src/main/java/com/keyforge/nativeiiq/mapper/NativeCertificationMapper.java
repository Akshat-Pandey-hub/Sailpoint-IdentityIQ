package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeCertificationRow;
import com.keyforge.nativeiiq.wire.NativeSerialize;

import sailpoint.object.Certification;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Identity;
import sailpoint.object.SignOffHistory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a native {@code sailpoint.object.Certification} into a {@link NativeCertificationRow}. Read-only;
 * only getters verified against the 8.4 {@code identityiq.jar}. CertificationGroup/Definition preserved as
 * reference ids/names. Sign-off history flattened to plain maps. Nothing inferred.
 */
public final class NativeCertificationMapper {

    private NativeCertificationMapper() {
    }

    public static NativeCertificationRow map(Certification c, String sourceSystem, String extractionRunId) {
        NativeCertificationRow row = new NativeCertificationRow();

        row.setSourceId(c.getId());
        row.setName(c.getName());
        row.setCertificationName(c.getCertificationName());
        row.setShortName(c.getShortName());
        row.setType(enumName(c.getType()));
        row.setPhase(enumName(c.getPhase()));
        row.setComments(c.getComments());
        row.setCreator(c.getCreator());
        row.setManager(c.getManager());

        List<CertificationGroup> groups = c.getCertificationGroups();
        if (groups != null && !groups.isEmpty() && groups.get(0) != null) {
            row.setCertificationGroupId(groups.get(0).getId());
            row.setCertificationGroupName(groups.get(0).getName());
        }
        row.setCertificationDefinitionId(c.getCertificationDefinitionId());
        row.setGroupDefinitionId(c.getGroupDefinitionId());
        row.setGroupDefinitionName(c.getGroupDefinitionName());
        row.setApplicationId(c.getApplicationId());
        row.setTaskScheduleId(c.getTaskScheduleId());
        row.setTriggerId(c.getTriggerId());
        Certification parent = c.getParent();
        if (parent != null) {
            row.setParentId(parent.getId());
        }

        row.setComplete(Boolean.valueOf(c.isComplete()));
        row.setExpired(Boolean.valueOf(c.isExpired()));
        row.setContinuous(Boolean.valueOf(c.isContinuous()));
        row.setElectronicallySigned(Boolean.valueOf(c.isElectronicallySigned()));

        row.setSigned(toInstant(c.getSigned()));
        row.setFinished(toInstant(c.getFinished()));
        row.setActivated(toInstant(c.getActivated()));
        row.setExpiration(toInstant(c.getExpiration()));
        row.setCreated(toInstant(c.getCreated()));
        row.setModified(toInstant(c.getModified()));

        row.setTotalItems(Integer.valueOf(c.getTotalItems()));
        row.setCompletedItems(Integer.valueOf(c.getCompletedItems()));
        row.setOpenItems(Integer.valueOf(c.getOpenItems()));
        row.setTotalEntities(Integer.valueOf(c.getTotalEntities()));
        row.setCompletedEntities(Integer.valueOf(c.getCompletedEntities()));
        row.setOpenEntities(Integer.valueOf(c.getOpenEntities()));
        row.setPercentComplete(Integer.valueOf(c.getPercentComplete()));

        List<String> certifiers = c.getCertifiers();
        if (certifiers != null) {
            for (String s : certifiers) {
                if (s != null) {
                    row.getCertifiers().add(s);
                }
            }
        }
        List<SignOffHistory> signOffs = c.getSignOffHistory();
        if (signOffs != null) {
            for (SignOffHistory s : signOffs) {
                if (s != null) {
                    row.getSignOffHistory().add(signOff(s));
                }
            }
        }

        Identity owner = c.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }

        // Additional native config fields (source-truth).
        sailpoint.object.Reference approverRule = c.getApproverRule();
        row.setApproverRule(approverRule == null ? null : approverRule.getName());
        row.setAutomaticClosingDate(NativeSerialize.iso(c.getAutomaticClosingDate()));
        List<String> statuses = new ArrayList<String>();
        if (c.getAllowedStatuses() != null) {
            for (Object s : c.getAllowedStatuses()) {
                statuses.add(NativeSerialize.enumName(s));
            }
        }
        row.setAllowedStatuses(NativeSerialize.jsonArray(statuses));
        List<String> tagNames = new ArrayList<String>();
        if (c.getTags() != null) {
            for (sailpoint.object.Tag t : c.getTags()) {
                if (t != null && t.getName() != null) {
                    tagNames.add(t.getName());
                }
            }
        }
        row.setTags(NativeSerialize.jsonArray(tagNames));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Map<String, Object> signOff(SignOffHistory s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("signerName", s.getSignerName());
        m.put("signerDisplayName", s.getSignerDisplayName());
        m.put("date", iso(s.getDate()));
        m.put("text", s.getText());
        return m;
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String iso(Date d) {
        return d == null ? null : d.toInstant().toString();
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
