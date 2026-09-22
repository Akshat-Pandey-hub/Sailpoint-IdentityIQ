package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeGroupDefinitionRow;

import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;

import java.time.Instant;
import java.util.Date;

/**
 * Maps a native {@code sailpoint.object.GroupDefinition} into a {@link NativeGroupDefinitionRow}.
 * Read-only: only getters. Uses only getters verified against the real 8.4 {@code identityiq.jar}.
 * Nothing is inferred beyond the authoritative native distinction below.
 *
 * <p><b>Group vs Population (source-backed):</b> IIQ distinguishes the two by the presence of a
 * {@code GroupFactory}. A definition tied to a factory ({@code getFactory() != null}) is a
 * factory-generated <b>GROUP</b>; a definition with no factory is a filter-defined <b>POPULATION</b>.
 * The derived {@code type} plus the raw {@code factoryId}/{@code factoryName} are both stored so the
 * classification is auditable from the source, not guessed. The {@code filter} is stored as its
 * expression text (intrinsic to the definition); no membership is materialized in this stage.
 */
public final class NativeGroupDefinitionMapper {

    public static final String TYPE_GROUP = "GROUP";
    public static final String TYPE_POPULATION = "POPULATION";

    private NativeGroupDefinitionMapper() {
    }

    public static NativeGroupDefinitionRow map(GroupDefinition gd, String sourceSystem, String extractionRunId) {
        NativeGroupDefinitionRow row = new NativeGroupDefinitionRow();

        row.setSourceId(gd.getId());
        row.setName(gd.getName());

        // Authoritative native distinction: factory present => GROUP, absent => POPULATION.
        GroupFactory factory = gd.getFactory();
        row.setType(factory != null ? TYPE_GROUP : TYPE_POPULATION);
        if (factory != null) {
            row.setFactoryId(factory.getId());
            row.setFactoryName(factory.getName());
        }

        row.setFilterExpression(filterExpression(gd.getFilter()));

        row.setIsPrivate(Boolean.valueOf(gd.isPrivate()));
        row.setIndexed(Boolean.valueOf(gd.isIndexed()));
        row.setNullGroup(Boolean.valueOf(gd.isNullGroup()));
        row.setNameUnique(Boolean.valueOf(gd.isNameUnique()));

        Identity owner = gd.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }

        row.setLastRefresh(toInstant(gd.getLastRefresh()));
        row.setCreated(toInstant(gd.getCreated()));
        row.setModified(toInstant(gd.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    /** The filter's expression text; a filter that cannot render never fails the row. */
    private static String filterExpression(Filter filter) {
        if (filter == null) {
            return null;
        }
        try {
            return filter.getExpression();
        } catch (Throwable t) {
            return "<unrenderable-filter:" + filter.getClass().getName() + ">";
        }
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
