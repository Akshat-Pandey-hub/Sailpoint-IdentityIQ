# KeyForge Native IIQ Extractor — IdentityIQ Plugin

A read-only IdentityIQ plugin that exposes IdentityIQ objects through the native Java API as REST endpoints under
`/identityiq/plugin/rest/keyForgeNativeIIQ/`. The KeyForge KF Agent (`iiq-migration-tool.jar start-kfagent`) calls
these endpoints and serves the data as `/kfagent/*`.

- **Read-only:** never creates, modifies or deletes anything in IdentityIQ.
- **Secured:** every endpoint requires an IdentityIQ **System Administrator**.
- **Requires** IdentityIQ 8.4 or later.

## Contents of the ZIP (`KeyForgeNativeIIQ-1.0.zip`)

```
manifest.xml                 plugin descriptor (registers the REST resources)
lib/KeyForgeNativeIIQ.jar    the plugin classes (com.keyforge.nativeiiq.* only)
README.md                    this file
```

`sailpoint.*` and `javax.ws.rs.*` are supplied by the IdentityIQ plugin runtime and are not bundled.

## Install or upgrade

1. Log in to IdentityIQ as a System Administrator.
2. Open the gear menu → **Plugins**.
3. Click **Install** (or **Import**) and upload `KeyForgeNativeIIQ-1.0.zip`.
4. Confirm **KeyForge Native IIQ Extractor** appears in the plugin list and is enabled.

No IdentityIQ restart and no server file access is required. Installing a newer ZIP the same way replaces the
previous version.

## Incremental reads

Most endpoints accept an optional `modifiedAfter` (ISO-8601, e.g. `2026-10-12T10:00:00Z`) and then return only the
objects changed after it (modified after it, or never modified and created after it; for append-only event objects,
created after it). Without the parameter the full population is returned.

## Build

Requires `lib/identityiq.jar` (from the IdentityIQ installation) in the repository's `lib/` folder:

```
mvn -Pnative clean package
```

Output: `target/dist/KeyForgeNativeIIQ-1.0.zip`. The normal build (`mvn clean package`) produces the KF Agent jar and
does not need `identityiq.jar`.
