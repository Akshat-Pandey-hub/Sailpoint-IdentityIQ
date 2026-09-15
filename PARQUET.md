# KeyForge IIQ → Parquet Analytics / Event-Store Workstream

This is a **separate extraction path** from the PostgreSQL migration. It is **not** a
PostgreSQL→Parquet conversion — the Parquet datasets are produced **directly from IdentityIQ** using
the same verified source interfaces the DB path uses, and (Phase 2) queried by a REST service that
reads the Parquet files, never PostgreSQL.

```
SailPoint IdentityIQ
        │  (SCIM / classic UI-REST, same auth as the DB path)
        ▼
Parquet Extraction Layer   (com.keyforge.iiq.parquet)
        │  reuse existing Services + RowMappers, attach lineage envelope
        ▼
Parquet datasets/files     (PARQUET_OUT_DIR/<dataset>/part-<extraction_run_id>.parquet)
        │
        ▼
REST Query Service         (Phase 2 — not yet built)
```

The existing PostgreSQL extraction pipeline is untouched: this workstream adds new packages only and
does not modify any existing table, command, or extractor.

## Phase 1 — direct IIQ → Parquet (built & validated)

### Configuration (env-first, same convention as the rest of the tool)
- `IIQ_BASE_URL`, `IIQ_USERNAME`, `IIQ_PASSWORD` — IdentityIQ connection (reused).
- `PARQUET_OUT_DIR` — output directory (default `parquet-data`).
- No PostgreSQL settings are required or used by this path.

### Run the extraction — one independent command per dataset
Each dataset has its own extraction command (mirroring the per-entity PostgreSQL extractors). Each
extracts directly from IIQ, writes **only** its own Parquet dataset, and reports its own
extracted/written/failed counts. They are independently executable and fail independently.
```
java -jar target/iiq-migration-tool.jar extract-task-results-parquet
java -jar target/iiq-migration-tool.jar extract-audit-events-parquet
java -jar target/iiq-migration-tool.jar extract-access-requests-parquet
java -jar target/iiq-migration-tool.jar extract-request-items-parquet
java -jar target/iiq-migration-tool.jar extract-request-approvals-parquet
java -jar target/iiq-migration-tool.jar extract-provisioning-transactions-parquet
java -jar target/iiq-migration-tool.jar extract-provisioning-items-parquet
java -jar target/iiq-migration-tool.jar extract-violations-parquet
java -jar target/iiq-migration-tool.jar extract-cert-item-decisions-parquet
java -jar target/iiq-migration-tool.jar extract-event-links-parquet

# orchestration only: invokes every individual extractor above
java -jar target/iiq-migration-tool.jar extract-all-parquet
```
Each run generates one `extraction_run_id` and writes one **append-oriented** part file per dataset:
```
PARQUET_OUT_DIR/
  task_result/part-<run>.parquet
  kf_audit_event/part-<run>.parquet
  kf_access_request/part-<run>.parquet
  kf_request_item/part-<run>.parquet
  kf_request_approval/part-<run>.parquet
  kf_provisioning_txn/part-<run>.parquet
  kf_provisioning_item/part-<run>.parquet
  kf_violation/part-<run>.parquet
  kf_cert_item_decision/part-<run>.parquet   (schema only; source-limited)
  kf_event_link/part-<run>.parquet
```
Historical/event-shaped datasets are **append-only**: each run adds a new part file; prior files are
never overwritten. The tool reports per-dataset `extracted` / `written` / errors and the run id.

> DuckDB (embedded, no Hadoop) writes the Parquet files. It extracts a native library to a temp dir;
> the tool points that at `PARQUET_OUT_DIR/.duckdb-tmp` so extraction works even when the system temp
> volume is low on space.

### Lineage envelope (PDF §6) — attached to every record
`src_system, src_object_type, src_object_id, src_natural_key, src_created, src_modified,
src_event_ts, extracted_at, extraction_run_id, src_interface, record_hash, raw_ref`.
`record_hash` is a deterministic SHA-256 over the business fields (basis for later change detection);
any value the source does not expose is left NULL — never fabricated.

### Datasets, sources, and completeness
| Dataset | IIQ source (verified) | Source-complete? |
|---|---|---|
| `task_result` | SCIM `/TaskResults` | complete |
| `kf_audit_event` | `analyze/audit/auditDataSource.json` | complete (5 source fields) |
| `kf_access_request` / `kf_request_item` / `kf_request_approval` | `ui/rest/identityRequests` | complete |
| `kf_provisioning_txn` | `rest/provisioningTransactions` (list) | complete |
| `kf_provisioning_item` | `rest/provisioningTransactions/{id}` (detail plan) | complete |
| `kf_violation` | SCIM `/PolicyViolations` | complete (0 live records) |
| `kf_cert_item_decision` | — | **source-limited**: decision hierarchy not reachable read-only (admin UI drill-in 500, no plugin). Schema written, 0 rows. |
| `kf_event_link` | derived: audit→identity/account/entitlement (pure resolver over extracted universe) + provisioning-txn→request/certification (explicit `accessRequestId`/`certificationName` from detail) | complete for exposed references |

Phase 2 (REST query service over these Parquet datasets) is documented separately once built.
