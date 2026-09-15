package com.keyforge.iiq.canonical;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintains the derived {@code kf_identity_account} view and reports the state of the four core
 * canonical entities — which are now PHYSICAL tables produced by the normal persistence path.
 *
 * <p>Scope is deliberately narrow and non-destructive: it never creates the four core canonical
 * views (that would collide with the physical tables), never drops or alters anything, and refuses
 * to replace a physical table. On a fresh canonical schema it creates only {@code kf_identity_account}
 * over the physical {@code kf_account}/{@code kf_identity} tables; on the legacy validated schema it
 * builds the same view over the physical {@code account}/{@code usr} tables (identical semantics).
 */
public class CanonicalViewReconciliationService {

    private final CanonicalViewRepository repo;

    public CanonicalViewReconciliationService() {
        this(new CanonicalViewRepository());
    }

    public CanonicalViewReconciliationService(String schema) {
        this(new CanonicalViewRepository(schema));
    }

    public CanonicalViewReconciliationService(CanonicalViewRepository repo) {
        this.repo = repo;
    }

    public CanonicalViewRepository repository() {
        return repo;
    }

    /** State of a core canonical entity name in the target schema. */
    public enum CoreState { PHYSICAL_TABLE, LEGACY_VIEW, ABSENT }

    /** Outcome for the derived identity_account view. */
    public enum LinkOutcome { CREATED, REPLACED, CONFLICT_SKIPPED, SOURCE_ABSENT }

    public record CoreReport(String name, CoreState state, String note) {
    }

    public static final class Result {
        private final List<CoreReport> core = new ArrayList<>();
        private LinkOutcome linkOutcome;
        private String linkNote = "";
        private String accountTable, identityTable;
        private long iaTotal = -1, iaResolved = -1, iaUnresolved = -1, iaNoRef = -1;

        public List<CoreReport> getCore() { return core; }
        public LinkOutcome getLinkOutcome() { return linkOutcome; }
        public String getLinkNote() { return linkNote; }
        public String getAccountTable() { return accountTable; }
        public String getIdentityTable() { return identityTable; }
        public long getIaTotal() { return iaTotal; }
        public long getIaResolved() { return iaResolved; }
        public long getIaUnresolved() { return iaUnresolved; }
        public long getIaNoRef() { return iaNoRef; }
    }

    public Result reconcile(Connection conn) throws SQLException {
        repo.ensureSchema(conn);
        Result result = new Result();

        // 1. Report core canonical entity state (never create/replace them).
        for (String name : CanonicalViewRepository.CORE_CANONICAL) {
            Character kind = repo.relationKind(conn, name);
            if (kind == null) {
                result.core.add(new CoreReport(name, CoreState.ABSENT,
                        "not present (run the extraction command to create the physical table)"));
            } else if (kind == 'r' || kind == 'p') {
                result.core.add(new CoreReport(name, CoreState.PHYSICAL_TABLE,
                        "canonical physical table present — no view needed"));
            } else if (kind == 'v') {
                result.core.add(new CoreReport(name, CoreState.LEGACY_VIEW,
                        "legacy compatibility view retained (left unchanged)"));
            } else {
                result.core.add(new CoreReport(name, CoreState.ABSENT,
                        "unexpected relkind='" + kind + "' — left unchanged"));
            }
        }

        // 2. Maintain kf_identity_account over whichever physical tables hold the data.
        maintainIdentityAccount(conn, result);
        return result;
    }

    private void maintainIdentityAccount(Connection conn, Result result) throws SQLException {
        String view = CanonicalViewRepository.V_IDENTITY_ACCOUNT;

        Character existing = repo.relationKind(conn, view);
        if (existing != null && existing != 'v') {
            result.linkOutcome = LinkOutcome.CONFLICT_SKIPPED;
            result.linkNote = "a physical relation (relkind='" + existing + "') already exists at "
                    + repo.qualified(view) + " — refusing to replace it";
            return;
        }

        String accountTable = repo.resolvePhysicalTable(conn, "kf_account", "account");
        String identityTable = repo.resolvePhysicalTable(conn, "kf_identity", "usr");
        result.accountTable = accountTable;
        result.identityTable = identityTable;
        if (accountTable == null) {
            result.linkOutcome = LinkOutcome.SOURCE_ABSENT;
            result.linkNote = "no physical account table (kf_account or account) present";
            return;
        }
        if (identityTable == null) {
            result.linkOutcome = LinkOutcome.SOURCE_ABSENT;
            result.linkNote = "no physical identity table (kf_identity or usr) present";
            return;
        }

        boolean replacing = existing != null;
        repo.execute(conn, repo.identityAccountViewSql(accountTable, identityTable));
        result.linkOutcome = replacing ? LinkOutcome.REPLACED : LinkOutcome.CREATED;
        result.linkNote = "over " + repo.qualified(accountTable) + " ⋈ " + repo.qualified(identityTable);

        result.iaTotal = repo.count(conn, view);
        result.iaResolved = repo.scalarLong(conn,
                "SELECT count(*) FROM " + repo.qualified(view) + " WHERE resolution_status = 'RESOLVED'");
        result.iaUnresolved = repo.scalarLong(conn,
                "SELECT count(*) FROM " + repo.qualified(view) + " WHERE resolution_status = 'UNRESOLVED'");
        result.iaNoRef = repo.scalarLong(conn,
                "SELECT count(*) FROM " + repo.qualified(view) + " WHERE resolution_status = 'NO_IDENTITY_REF'");
    }
}
