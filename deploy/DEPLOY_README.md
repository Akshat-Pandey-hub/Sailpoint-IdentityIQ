# KF Agent — Linux server deployment (handover)

## 1. What gets uploaded / handed over

| # | File | Goes where | Purpose |
|---|------|-----------|---------|
| 1 | `iiq-migration-tool.jar` (~78 MB) | the **KF Agent Linux server**, in one folder (e.g. `/opt/kfagent/`) | the whole application — REST service + all extraction jobs |
| 2 | `kfagent.env` (made from `kfagent.env.example`) | **same folder** on the server | the credentials / settings (env vars) |
| 3 | `KeyForgeNativeIIQ-1.0.zip` (~465 KB) | **inside IdentityIQ** (plugin admin UI) — NOT the KF Agent folder | the IIQ plugin the app reads data from. **Required**, installed separately in IIQ |

You do **NOT** upload: the source code, the `target/` folder, `.git`, Maven, or anything else.
You do **NOT** compile on the server — the JAR is already built on Windows (`mvn package`). The
server only needs **Java 17** to run it.

The JAR is a self-contained "fat jar" (all libraries, incl. the Linux DuckDB native, are inside it),
so nothing else needs to be uploaded beside it.

## 2. Prerequisites for the server (the admin confirms these)

- **Java 17+** installed (`java -version` must say 17 or higher). The app will NOT run on Java 11.
- Network access from this server to `IIQ_BASE_URL` over HTTPS.
- The **KeyForgeNativeIIQ plugin is installed and enabled in IdentityIQ** — without it, every
  endpoint and extraction job returns nothing.
- For the `extract-*-db` jobs only: network access to PostgreSQL (`PG_HOST`).
- The port (default **8100**) opened in the firewall if it will be reached from another machine (Postman).

## 3. Folder layout on the server

```
/opt/kfagent/
├── iiq-migration-tool.jar
├── kfagent.env              # from kfagent.env.example, real values, chmod 600
└── parquet-data/            # (optional) only if you use the Parquet query endpoints
```

## 4. Load the settings, then run

```bash
cd /opt/kfagent
set -a; source kfagent.env; set +a     # load the env vars into the shell
java -version                          # must be 17+
```

### Start the REST service (long-running; KF Agent + Parquet query on ONE port 8100)
```bash
java -jar iiq-migration-tool.jar start-kfagent
```
Serves on `http://<server>:8100/` :
- `/health`
- `/kfagent/*`            (native data as JSON)
- `/iiq_parquet/*`, `/native_parquet/*`   (Parquet query API — needs datasets present)

### Run an extraction job (one-off; writes to PostgreSQL)
Each is the same `java -jar … <command>` you run on Windows, e.g.:
```bash
java -jar iiq-migration-tool.jar extract-native-identity-db
java -jar iiq-migration-tool.jar extract-native-entitlement-db
# … etc. (full list: run `java -jar iiq-migration-tool.jar` with no command to print help)
```
These need the `PG_*` variables (already in `kfagent.env`).

## 5. Which commands need which variables

- `start-kfagent` → needs only `IIQ_BASE_URL/USERNAME/PASSWORD` (+ optional `KFAGENT_*`, `PARQUET_OUT_DIR`). **No PostgreSQL.**
- `extract-*-db` / `derive-*-db` / `reconcile-*-db` → need `IIQ_*` **and** `PG_*`.
- `extract-*-parquet` → need `IIQ_*` and `PARQUET_OUT_DIR`.
- `start-rest` (standalone Parquet, optional) → needs only `PARQUET_OUT_DIR` (+ optional `REST_HOST/REST_PORT`).

## 6. Keeping it running (admin decides)

For production the admin typically runs `start-kfagent` as a **systemd service** that reads
`EnvironmentFile=/opt/kfagent/kfagent.env` and restarts on failure, so it survives logout/reboot and
the password never appears in the shell history or `ps`. If systemd isn't available, `nohup`/`tmux`
works for a manual run. The extraction jobs are usually run on demand or on a schedule (cron), not as
a long-running service.

## 7. Updating later

Rebuild on Windows (`mvn package`), upload the new `iiq-migration-tool.jar` over the old one (keep a
`.bak` copy first), and restart the service. Only re-upload the plugin ZIP to IIQ when the **plugin**
code changed (new/changed native entity); pure REST/endpoint changes need only the new JAR.
