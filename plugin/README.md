# KeyForge Native IIQ Extractor — IdentityIQ Plugin (Stage 1: Identity)

An installable IdentityIQ plugin that runs the native (Java-API) Identity extraction **inside** the
live IIQ runtime. It is built from the same repository (`com.keyforge.nativeiiq.*`); the existing
REST/SCIM tool is untouched.

## What's in the ZIP (`target/dist/KeyForgeNativeIIQ-1.0.zip`)
```
manifest.xml                                  (plugin descriptor)
lib/KeyForgeNativeIIQ.jar                      (our com.keyforge.nativeiiq.* classes only)
import/install/KeyForgeNativeIdentityExtractTask.xml   (TaskDefinition, imported on install)
```
`sailpoint.*` is NOT bundled — the IIQ plugin runtime supplies it. The jar holds only our classes:
`model.NativeIdentityRow`/`NativeAccountRef`/`NativeExtractionResult`, `config.NativeExtractionConfig`,
`mapper.NativeIdentityMapper`, `source.NativeIdentityExtractor`, `task.NativeIdentityExtractTask`.

## Build the ZIP
Requires `lib/identityiq.jar` (from your IIQ install media / whoever deployed preview.keyforge.ai —
**not** the server filesystem) placed in the repo `lib/` folder, and `iiq.java.release` set to the
IIQ server's Java version. Then:
```
mvn -Pnative -Dmaven.repo.local=D:/m2/repository clean package
```
Output: `target/dist/KeyForgeNativeIIQ-1.0.zip`. The default REST build (`mvn clean package`) is
unaffected and never needs the jar.

## Install into IdentityIQ (web UI only — no server filesystem access needed)
1. Log in to `https://preview.keyforge.ai/identityiq/` as an admin (spadmin).
2. Go to the gear/Setup menu → **Plugins**.
3. **Install / Import** the `KeyForgeNativeIIQ-1.0.zip` (drag-and-drop).
4. IIQ imports `manifest.xml` (creates the Plugin) and the TaskDefinition from `import/install/`.

## Run (read-only)
Setup → **Tasks** → **"KeyForge Native Identity Extract"** → **Run**. It reads up to `identityLimit`
Identities via the native API and reports counts on the Task Result. Set `identityLimit=0` for all.
It performs no writes to IIQ.

## Before install, confirm against the live version
- `manifest.xml` `minSystemVersion` → set to the preview.keyforge.ai IIQ version.
- The `TaskDefinition` and `sailpoint.*` getter/`TaskExecutor` signatures compile against the real
  `identityiq.jar`; the compiler flags any version-specific differences on the first `-Pnative` build.
