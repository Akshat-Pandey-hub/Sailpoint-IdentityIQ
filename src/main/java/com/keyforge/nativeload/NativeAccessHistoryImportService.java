package com.keyforge.nativeload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the append-only import of ONE native Access-History table: page the plugin endpoint, parse the
 * {@code {sourceCount, rows:[…]}} envelope, and {@code append} every row through a {@link NativeAccessHistoryRepo}.
 * Append-only — no deletion sweep (Access-History objects are immutable historical evidence). A
 * confirmed-complete-scan guard throws if fewer rows were seen than the source {@code countObjects}, so a
 * partial pull is never reported as complete. Re-runs insert nothing new (ON CONFLICT DO NOTHING) → idempotent.
 */
public final class NativeAccessHistoryImportService {

    public static final class Result {
        private final String entity;
        private final String targetTable;
        private final int extracted;
        private final int inserted;
        private final int skipped;
        private final int failed;
        private final int sourceCount;
        private final List<String> failures;

        Result(String entity, String targetTable, int extracted, int inserted, int skipped, int failed,
               int sourceCount, List<String> failures) {
            this.entity = entity;
            this.targetTable = targetTable;
            this.extracted = extracted;
            this.inserted = inserted;
            this.skipped = skipped;
            this.failed = failed;
            this.sourceCount = sourceCount;
            this.failures = failures;
        }

        public String getEntity() { return entity; }
        public String getTargetTable() { return targetTable; }
        public int getExtracted() { return extracted; }
        public int getInserted() { return inserted; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        public int getSourceCount() { return sourceCount; }
        public List<String> getFailures() { return failures; }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Connection conn;
    private final NativeAccessHistoryClient client;
    private final String path;
    private final NativeAccessHistoryRepo repo;
    private final int pageSize;

    public NativeAccessHistoryImportService(Connection conn, NativeAccessHistoryClient client, String path,
                                            NativeAccessHistoryRepo repo, int pageSize) {
        this.conn = conn;
        this.client = client;
        this.path = path;
        this.repo = repo;
        this.pageSize = pageSize > 0 ? pageSize : 100;
    }

    public Result importAll() throws SQLException {
        repo.ensureTargetTable(conn);

        int extracted = 0;
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        int sourceCount = -1;
        List<String> failures = new ArrayList<String>();

        int start = 0;
        while (true) {
            JsonNode envelope = parse(client.fetchPage(path, start, pageSize));
            JsonNode err = envelope.get("error");
            if (err != null && !err.isNull()) {
                throw new NativeImportException(repo.entity() + " endpoint returned an error: "
                        + NativeJsonRead.text(err, "type") + ": " + NativeJsonRead.text(err, "message"));
            }
            int pageSourceCount = envelope.path("sourceCount").asInt(-1);
            if (pageSourceCount >= 0) {
                if (sourceCount >= 0 && sourceCount != pageSourceCount) {
                    throw new NativeImportException(repo.entity() + " source count changed during paginated "
                            + "extraction: " + sourceCount + " -> " + pageSourceCount);
                }
                sourceCount = pageSourceCount;
            }
            JsonNode rows = envelope.path("rows");
            if (!rows.isArray() || rows.size() == 0) {
                break;
            }
            for (JsonNode row : rows) {
                extracted++;
                try {
                    if (repo.append(conn, row)) {
                        inserted++;
                    } else {
                        skipped++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add(NativeJsonRead.text(row, "sourceId") + ": " + e.getMessage());
                    }
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        if (sourceCount >= 0 && extracted != sourceCount) {
            throw new NativeImportException("Incomplete " + repo.entity() + " scan: sourceCount=" + sourceCount
                    + ", extracted=" + extracted);
        }
        return new Result(repo.entity(), repo.targetTable(), extracted, inserted, skipped, failed, sourceCount,
                failures);
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new NativeImportException("Empty Access-History response payload");
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new NativeImportException("Unparseable Access-History response: " + e.getMessage(), e);
        }
    }
}
