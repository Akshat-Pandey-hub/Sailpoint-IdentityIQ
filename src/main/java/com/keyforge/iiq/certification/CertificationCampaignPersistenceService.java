package com.keyforge.iiq.certification;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists certification campaigns into {@code kf_certification_campaign}. Each row runs in its own
 * savepoint; a bad record is reported and skipped, never dropped. Idempotent via the deterministic
 * {@code campaignid} PK + upsert. Empty source is a valid empty result.
 */
public class CertificationCampaignPersistenceService {

    private final CertificationCampaignRepository repository;

    public CertificationCampaignPersistenceService() {
        this(new CertificationCampaignRepository());
    }

    public CertificationCampaignPersistenceService(String schema) {
        this(new CertificationCampaignRepository(schema));
    }

    public CertificationCampaignPersistenceService(CertificationCampaignRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    public static final class Result {
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getFailed() { return failed; }
        public int getPersisted() { return inserted + updated; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn, List<CertificationCampaign> campaigns) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (CertificationCampaign c : campaigns == null ? List.<CertificationCampaign>of() : campaigns) {
                persistOne(conn, c, result);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        return result;
    }

    private void persistOne(Connection conn, CertificationCampaign c, Result result) throws SQLException {
        CertificationCampaignRow row;
        try {
            row = CertificationCampaignRowMapper.map(c);
        } catch (CertificationCampaignMappingException e) {
            result.failed++;
            result.failures.add("campaign '" + c.name() + "' (id=" + c.id() + ") -> " + e.getMessage());
            return;
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            CertificationCampaignRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == CertificationCampaignRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("campaign '" + c.name() + "' (id=" + c.id() + ") -> " + e.getMessage());
        }
    }
}
