package com.keyforge.nativeiiq.resource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed allowlist and classpath loading for the report SQL dialect variants. */
final class NativeSqlReportResources {

    private static final Map<String, Variants> REPORTS = new LinkedHashMap<String, Variants>();

    static {
        // PostgreSQL SQL paths are the pre-existing resources and remain unchanged.
        // The two entitlement certification variants convert epoch milliseconds to UTC date text;
        // Oracle variants use NUMBER/NVL/NUMTODSINTERVAL and PostgreSQL variants use BIGINT/to_timestamp.
        // Assignment intentionally keeps role_requestable NULL: the mapped Bundle has no equivalent field.
        // Its membership join uses the Identity.hbm.xml link column workgroup (not workgroup_id).
        REPORTS.put("entitlementAssignment", new Variants("entitlementAssignment"));
        REPORTS.put("workgroupMembers", new Variants("workgroupMembers"));
        REPORTS.put("entitlementCertification", new Variants("entitlementCertification"));
        REPORTS.put("entitlementCertificationStatus", new Variants("entitlementCertificationStatus"));
        // Assignment Boolean syntax is dialect-specific; report aliases and CASE outputs are shared.
        // Certification history keeps NULL decisions last so a pending action cannot outrank a decision.
        // Workgroup membership deletions remain undetectable by modifiedAfter after the link is removed.
    }

    private NativeSqlReportResources() { }

    static boolean isRegistered(String report) {
        return REPORTS.containsKey(report);
    }

    static String resourcePathFor(String report, NativeSqlReportDialect dialect) {
        Variants variants = REPORTS.get(report);
        if (variants == null) {
            return null;
        }
        if (dialect == null) {
            throw new IllegalArgumentException("SQL report database dialect is missing");
        }
        return dialect == NativeSqlReportDialect.POSTGRESQL ? variants.postgresql : variants.oracle;
    }

    /** The bundled SQL file name for a resource path, e.g. {@code entitlementAssignment.oracle.sql}. */
    static String fileName(String resourcePath) {
        if (resourcePath == null) {
            return null;
        }
        int slash = resourcePath.lastIndexOf('/');
        return slash < 0 ? resourcePath : resourcePath.substring(slash + 1);
    }

    static String loadSql(String resourcePath) throws Exception {
        InputStream in = NativeSqlReportResources.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("bundled SQL resource not found: " + resourcePath);
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        String sql = sb.toString().trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql;
    }

    static boolean isSingleSelect(String sql) {
        if (sql == null) {
            return false;
        }
        String statement = sql.trim();
        if (statement.isEmpty()) {
            return false;
        }
        String upper = statement.toUpperCase(java.util.Locale.ROOT);
        return (upper.startsWith("SELECT") || upper.startsWith("WITH"))
                && statement.indexOf(';') < 0;
    }

    private static final class Variants {
        private final String postgresql;
        private final String oracle;

        private Variants(String name) {
            this.postgresql = "/com/keyforge/nativeiiq/sql/" + name + ".sql";
            this.oracle = "/com/keyforge/nativeiiq/sql/" + name + ".oracle.sql";
        }
    }
}
