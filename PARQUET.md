# Native Parquet Datasets

Builds Parquet datasets from the **native (IIQ Java API) layer** and serves them through a read-only query API.

```
IdentityIQ ─(KeyForgeNativeIIQ plugin)─▶ extract-native-*-db ─▶ PostgreSQL (iiq_native)
                                                                    │
                                              extract-native-parquet ▼
                                         PARQUET_OUT_DIR/<dataset>/current.parquet
                                                                    │
                                     start-kfagent / start-rest ─▶ GET /native_parquet/*
```

## 1. Prerequisite: load the native tables

`extract-native-parquet` reads the `iiq_native` PostgreSQL schema, so load it first with the native jobs
(`extract-native-*-db`, and `derive-native-events-db` for `kf_event_link`).

## 2. Build the datasets: one command

```bash
java -jar target/iiq-migration-tool.jar extract-native-parquet
```

Writes all 10 datasets, one stable file each, at `<PARQUET_OUT_DIR>/<dataset>/current.parquet`:

| Dataset | Source table (`iiq_native`) |
|---|---|
| `kf_access_request` | `kf_identity_request` |
| `kf_request_item` | `kf_identity_request_item` |
| `kf_request_approval` | `kf_identity_request_approval` |
| `kf_provisioning_txn` | `kf_provisioning_txn` |
| `kf_provisioning_item` | `kf_provisioning_item` |
| `kf_cert_item_decision` | `kf_certification_item` |
| `kf_violation` | `kf_violation` |
| `kf_audit_event` | `kf_audit_event` |
| `task_result` | `kf_task_result` |
| `kf_event_link` | `kf_event_link` |

Each file is current-state (active rows only) and deduplicated by primary key using a content hash of the
business columns. Re-running with unchanged source data reproduces the same rows. No per-run versions are kept.

**Configuration:**

| Variable | Default | Purpose |
|---|---|---|
| `PG_HOST`, `PG_PORT`, `PG_DATABASE`, `PG_USERNAME`, `PG_PASSWORD` | — | PostgreSQL holding the native tables |
| `IIQ_NATIVE_SCHEMA` | `iiq_native` | Native source schema |
| `PARQUET_OUT_DIR` | `./parquet-data` | Output directory |

## 3. Query the datasets

The query API is served by `start-kfagent` (on the KF Agent port, default 8100) or standalone by `start-rest`.
Every request requires **HTTP Basic Auth** with the configured `IIQ_USERNAME` / `IIQ_PASSWORD`.

| Endpoint | Returns |
|---|---|
| `GET /native_parquet/datasets` | Dataset names |
| `GET /native_parquet/{dataset}/schema` | Column names and types |
| `GET /native_parquet/{dataset}` | Rows (query parameters below) |

**Query parameters** (validated against the dataset schema; an invalid field or operator returns 400):

- `fields=col1,col2`: projection
- `<field>=<value>`: equality filter
- `filter.<field>.<op>=<value>`, where `op` is one of `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `contains`,
  `startsWith`, `in` (comma list)
- `sort=<field>&order=asc|desc`
- `limit` (default 100, max 1000) and `offset`

**Response:**

```json
{ "dataset": "kf_provisioning_txn", "columns": ["..."], "rows": [ { } ], "returned": 2, "total": 42, "limit": 100, "offset": 0 }
```

**Examples:**

```
GET /native_parquet/kf_audit_event?limit=100
GET /native_parquet/kf_provisioning_txn?status=Committed&limit=50
GET /native_parquet/kf_access_request?sort=created_at&order=desc&limit=20
GET /native_parquet/kf_event_link?filter.link_type.eq=TASK_TARGET
```

The API is read-only (GET only). Filter values are always bound as parameters, and no SQL or file paths are
exposed.
