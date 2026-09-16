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

## Phase 2 — REST query service over Parquet (built)

A read-only HTTP query service, a runtime mode of the **same JAR**. It queries the Parquet files with
DuckDB and **never** calls IdentityIQ or PostgreSQL. Uses the JDK's built-in HTTP server (no web
framework, no new dependency) + Jackson (already present) + DuckDB (already present).

### Start it
```
java -jar target/iiq-migration-tool.jar start-rest
```
Config (env-first): `REST_HOST` (default `127.0.0.1`), `REST_PORT` (default `8100`), `PARQUET_OUT_DIR`
(shared with extraction, default `parquet-data`). The process stays running until stopped (Ctrl-C).

### Endpoints
- `GET /health` → `{"status":"UP","parquetDirAccessible":true}` (no Parquet scan)
- `GET /iiq_parquet/datasets` → the 10 dataset names
- `GET /iiq_parquet/{dataset}/schema` → `{dataset, columns:[{name,type}]}` (from the real DatasetSpec)
- `GET /iiq_parquet/{dataset}` → query (below)

### Query response shape
```json
{ "dataset":"kf_audit_event", "columns":["audit_event_id","action"], "rows":[ {...} ],
  "returned": 2, "total": 42, "limit": 100, "offset": 0 }
```

### Query parameters (all validated against the dataset's real schema)
- **fields**: `?fields=col1,col2` — projection; unknown column → 400.
- **equality**: `?<field>=<value>` — e.g. `?action=LOGIN` (field must be a schema column).
- **operators**: `?filter.<field>.<op>=<value>` with `op` ∈ `eq, ne, gt, gte, lt, lte, contains,
  startsWith, in` (`in` takes a comma list). `filter.<field>=<value>` is equality. An operator that
  doesn't fit the column type (e.g. `gt` on text) → 400.
- **sort**: `?sort=<field>&order=asc|desc` (default asc; deterministic default order otherwise).
- **pagination**: `?limit=` (default 100, max 1000) `&offset=`.
- **run**: `?run=<extraction_run_id>` to read a specific run's file (default: latest).

### Which Parquet file is read (multiple runs)
Each extractor writes a **full snapshot** per run to `<dataset>/part-<run>.parquet`. The REST service
reads a **single** file — by default the **most recently written** part file (latest successful run,
by file modification time; run ids are random UUIDs and not time-ordered) — so records are never
duplicated across runs. `?run=<id>` selects a specific run. No filesystem paths are ever exposed.

### Safety
Read-only (GET only). Dataset names, column names, operators, and sort fields are validated against
the known schema; filter **values** are always bound as parameters (with a CAST to the column type).
No arbitrary SQL, no filesystem path access. Errors return JSON `{error, message}` with 400/404/500;
stack traces are never returned.

### Example requests (real dataset/column names)
```
GET /iiq_parquet/kf_audit_event?limit=100
GET /iiq_parquet/kf_audit_event?fields=audit_event_id,action,target
GET /iiq_parquet/kf_audit_event?action=LOGIN
GET /iiq_parquet/kf_audit_event?filter.extracted_at.gte=2026-07-01T00:00:00Z
GET /iiq_parquet/kf_provisioning_txn?status=Committed&limit=50
GET /iiq_parquet/kf_provisioning_item?request_type=attribute&fields=itemid,operation,name,result
GET /iiq_parquet/kf_event_link?filter.source_object_type.eq=ProvisioningTransaction
GET /iiq_parquet/kf_access_request?sort=created_at&order=desc&limit=20
```
