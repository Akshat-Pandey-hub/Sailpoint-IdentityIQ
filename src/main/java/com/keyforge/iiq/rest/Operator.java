package com.keyforge.iiq.rest;

import com.keyforge.iiq.parquet.ParquetType;

/**
 * The supported filter operators and which Parquet column types each is valid for. Operators map to a
 * fixed SQL fragment; filter <b>values</b> are always bound as parameters, never concatenated, so no
 * caller input reaches SQL as code.
 */
public enum Operator {
    eq, ne, gt, gte, lt, lte, contains, startsWith, in;

    public static Operator from(String token) {
        for (Operator o : values()) {
            if (o.name().equalsIgnoreCase(token)) {
                return o;
            }
        }
        throw ApiException.badRequest("Unknown filter operator: '" + token
                + "'. Supported: eq, ne, gt, gte, lt, lte, contains, startsWith, in");
    }

    /** Whether this operator makes sense for a column of the given type. */
    public boolean appliesTo(ParquetType type) {
        return switch (this) {
            case eq, ne, in -> true;                       // any type
            case gt, gte, lt, lte -> isOrdered(type);      // numeric / timestamp only
            case contains, startsWith -> isText(type);     // text only
        };
    }

    private static boolean isOrdered(ParquetType t) {
        return t == ParquetType.INT || t == ParquetType.LONG || t == ParquetType.DOUBLE
                || t == ParquetType.TIMESTAMP;
    }

    private static boolean isText(ParquetType t) {
        return t == ParquetType.STRING || t == ParquetType.UUID_STR || t == ParquetType.JSON;
    }
}
