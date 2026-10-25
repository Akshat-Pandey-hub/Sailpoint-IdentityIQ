# KF Agent — Linux server deployment (handover)

## 1. What gets uploaded / handed over

| # | File | Goes where | Purpose |
|---|------|-----------|---------|
| 1 | `iiq-migration-tool.jar` (~78 MB) | the **KF Agent Linux server**, in one folder (e.g. `/opt/kfagent/`) | the whole application — REST service + all extraction jobs |
| 2 | `kfagent.env` (made from `kfagent.env.example`) | **same folder** on the server | the credentials / settings (env vars) |
| 3 | `log4j2.properties` (from `src/main/resources/log4j2.properties`) | **same folder** on the KF Agent Linux server, e.g. `/opt/kfagent/` | external console + rolling-file logging configuration; deliberately not bundled in the JAR |
| 4 | `KeyForgeNativeIIQ-1.0.zip` (~465 KB) | **inside IdentityIQ** (plugin admin UI) — NOT the KF Agent folder | the IIQ plugin the app reads data from. **Required**, installed separately in IIQ |

You do **NOT** upload: the source code, the `target/` folder, `.git`, Maven, or anything else.
You do **NOT** compile on the server — the JAR is already built on Windows (`mvn package`). The
server only needs **Java 17** to run it.

The JAR is a self-contained "fat jar" (all libraries, incl. the Linux DuckDB native, are inside it). The
`log4j2.properties` file is intentionally separate and must be handed over beside it for the configured console
and rolling-file appenders. The JAR does not contain a bundled fallback configuration.

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
├── log4j2.properties       # separate Log4j2 configuration file
├── kfagent.env              # from kfagent.env.example, real values, chmod 600
├── logs/                    # kfagent.log + rotated kfagent-YYYY-MM-DD-N.log.gz (KF_LOG_DIR)
└── parquet-data/            # (optional) only if you use the Parquet query endpoints
```

Create the log folder once and let the service user write to it:
`mkdir -p /opt/kfagent/logs && chown <service-user> /opt/kfagent/logs`

## 4. Load the settings, then run

```bash
cd /opt/kfagent
set -a; source kfagent.env; set +a     # load the env vars into the shell
java -version                          # must be 17+
```

### Start the REST service (long-running; KF Agent + Parquet query on ONE port 8100)
```bash
java -Dlog4j2.configurationFile=/opt/kfagent/log4j2.properties -jar iiq-migration-tool.jar start-kfagent
```
Alternatively, set `LOG4J_CONFIGURATION_FILE=/opt/kfagent/log4j2.properties` in the service environment before
starting Java. The external file is selected by Log4j2 before the application's first logger is initialized.
Serves on `http://<server>:8100/` :
- `/health`
- `/kfagent/*`            (native data as JSON)
- `/iiq_parquet/*`, `/native_parquet/*`   (Parquet query API — needs datasets present)

Every endpoint requires **HTTP Basic Auth** with the client ID / client secret set as `KFAGENT_USERNAME` /
`KFAGENT_PASSWORD` in `kfagent.env` (in Postman: Authorization → Basic Auth). Missing or wrong credentials → `401`.
`IIQ_USERNAME` / `IIQ_PASSWORD` are used only for Part 1's own IdentityIQ login and are not accepted here.

### Run an extraction job (one-off; writes to PostgreSQL)
Each is the same `java -jar … <command>` you run on Windows, e.g.:
```bash
java -jar iiq-migration-tool.jar extract-native-identity-db
java -jar iiq-migration-tool.jar extract-native-entitlement-db
# … etc. (full list: run `java -jar iiq-migration-tool.jar` with no command to print help)
```
These need the `PG_*` variables (already in `kfagent.env`).

## 5. Which commands need which variables

- `start-kfagent` → needs `IIQ_BASE_URL/USERNAME/PASSWORD` (the IdentityIQ login) and `KFAGENT_USERNAME/KFAGENT_PASSWORD` (the REST Basic Auth client ID / secret; without both every request is rejected with 401) (+ optional `KFAGENT_HOST/KFAGENT_PORT`, `PARQUET_OUT_DIR`). **No PostgreSQL.**
- `extract-*-db` / `derive-*-db` / `reconcile-*-db` → need `IIQ_*` **and** `PG_*`.
- `extract-native-parquet` → needs `PG_*` (+ optional `IIQ_NATIVE_SCHEMA`, e.g. `iiq_native_part2` to read Part 2's tables) and `PARQUET_OUT_DIR`; see `PARQUET.md`. Use a `PARQUET_OUT_DIR` outside any code folder, restricted to the service account (the files hold identity data).
- `start-rest` (standalone Parquet, optional) → needs `PARQUET_OUT_DIR` and `KFAGENT_USERNAME/KFAGENT_PASSWORD` (the Basic Auth client ID / secret; without both every request is rejected with 401) (+ optional `REST_HOST/REST_PORT`).

## 6. Keeping it running (admin decides)

For production the admin typically runs `start-kfagent` as a **systemd service** that reads
`EnvironmentFile=/opt/kfagent/kfagent.env` and restarts on failure, so it survives logout/reboot and
the password never appears in the shell history or `ps`. If systemd isn't available, `nohup`/`tmux`
works for a manual run. The extraction jobs are usually run on demand or on a schedule (cron), not as
a long-running service.

## 6a. Logs

- **Where:** the console (`journalctl -u <service>` under systemd) and `KF_LOG_DIR/kfagent.log`.
- **Level:** `KF_LOG_LEVEL=normal` (default) | `debug` | `off`. `normal` logs each call and its outcome plus
  every error and rejected request with its reason; `debug` adds settings, credentials sent/expected and every
  IdentityIQ call (IIQ_PASSWORD is never printed); `off` drops routine lines but still logs errors with reasons.
- **Rotation:** the file rolls daily and at 10 MB into `kfagent-YYYY-MM-DD-N.log.gz`; at most 10 per day are kept
  and rolled files older than 30 days are deleted (checked whenever a roll happens).
- **Own configuration:** change the separately deployed `log4j2.properties` for format, sizes or retention.
  Point Log4j2 to it with `-Dlog4j2.configurationFile=/opt/kfagent/log4j2.properties` or set
  `LOG4J_CONFIGURATION_FILE=/opt/kfagent/log4j2.properties`. There is no bundled configuration fallback.

## 7. Updating later

Rebuild on Windows (`mvn package`), upload the new `iiq-migration-tool.jar` over the old one (keep a
`.bak` copy first), and restart the service. Only re-upload the plugin ZIP to IIQ when the **plugin**
code changed (new/changed native entity); pure REST/endpoint changes need only the new JAR.
