# RESEARCH — verified endpoints & schemas (live, fetched 2026-11-05)

## Mojang version manifest
- URL: `https://launchermeta.mojang.com/mc/game/version_manifest.json`
- Schema: `{"latest":{"release":..., "snapshot":...}, "versions":[{"id","type","url","time","releaseTime"}]}`
- NOTE: Today (2026-11) Mojang ships 26.x versions (latest release 26.3, snapshot 26.4-snapshot-7). Older releases 1.21.11, 1.20.x, ... down to 1.0 are ALSO listed. **No alpha/beta ids are present (count = 0).**
- Each version entry has a `url` to the individual version JSON at `https://piston-meta.mojang.com/v1/packages/{hash}/{version}.json`

## Individual version JSON (verified: 26.3 release, 1.8.1, 1.0)
Example host: `https://piston-meta.mojang.com/v1/packages/80608ff6753705b6a82e2cb86a5937d4d233abd4/26.3.json`

Modern (26.3) schema — confirmed `arguments` format:
- `arguments.game`: list of strings / {rules, value} objects (${placeholders})
- `arguments.jvm`: list (${classpath}, ${natives_directory}, etc.)
- `arguments["default-user-jvm"]`: JVM defaults with os rules
- `javaVersion.majorVersion` (e.g. 25 for 26.3); `component` (java-runtime-epsilon)
- `mainClass` (e.g. net.minecraft.client.main.Main)
- `downloads.client` {sha1,size,url} — host: `piston-data.mojang.com` (modern) / `launcher.mojang.com` (old)
- `libraries[].downloads.artifact` {path,sha1,size,url} — host: `libraries.minecraft.net`
- `libraries[].rules` [{action, os{name,arch,version}, features}]
- Natives (modern): classifier suffix `:natives-<os>` or `:<os>-<arch>` (e.g. `:linux-aarch_64`) with `os.name` rules
- `assetIndex` {id,sha1,size,totalSize,url}; `assets` id; layout = modern hashed (assetIndex) for 1.6+

OLD versions (1.8.1, 1.0) — **contradicts the "all modern arguments" assumption:**
- NO `arguments` key; uses `minecraftArguments` (single flat string)
- `javaVersion.component` = `jre-legacy`, majorVersion 8
- Natives use `downloads.classifiers` + `natives` map (`{"linux":"natives-linux"}`), no arch classifier
- `downloads.client` host: `launcher.mojang.com/v1/objects/...`
- 1.0 mainClass = `net.minecraft.launchwrapper.Launch`

## Fabric (verified: meta.fabricmc.net)
- Loader list: `https://meta.fabricmc.net/v2/versions/loader` → [{separator,build,maven,version,stable}]
- Game list: `https://meta.fabricmc.net/v2/versions/game` → [{version,stable}]
- Profile (verified 1.21.1 + 0.14.22): `https://meta.fabricmc.net/v2/versions/loader/{gameVersion}/{loaderVersion}/profile/json`
  - `inheritsFrom`: vanilla version
  - `mainClass`: `net.fabricmc.loader.impl.launch.knot.KnotClient` (replaces vanilla)
  - `arguments.game` (empty), `arguments.jvm` (Fabric JVM flags)
  - `libraries[]`: {name, url} only — **NO sha1/size/downloads**, host `maven.fabricmc.net`
  - So Fabric libs carry no integrity metadata in the plan; mark sha1/size as unresolved

## Download hosts observed
- client jar: `piston-data.mojang.com` (modern) / `launcher.mojang.com` (legacy)
- libraries: `libraries.minecraft.net`
- Fabric: `maven.fabricmc.net`
- asset index / assets: `piston-meta.mojang.com` / `piston-data.mojang.com` / `launcher.mojang.com` depending on era

## ManifestResolverKit
- Web search returned no results. **No evidence it exists, is maintained, or fits.** Not using it.

## JVM / AGP / Kotlin target (decision)
- `:core` is Kotlin/JVM. `:app` is Android (AGP). Latest stable AGP (8.x) requires JDK 17 to run and targets JVM 8 bytecode by default for library modules.
- Decision: **JVM 17 (LTS)** for `:core` compilation target & CI JDK. Reasoning: AGP 8.x runs on JDK 17; Kotlin/JVM 1.9/2.0 targets JVM 17 cleanly; keeps `:core` consumable by `:app` without a newer-JDK requirement. Record choice + sources in CI.
- Sources: Android Gradle Plugin requirements (JDK 17 for AGP 8+), Kotlin JVM target docs.

## Test fixture versions (chosen to satisfy Part A coverage)
| category | version id | rationale |
|---|---|---|
| pre-1.13 | 1.8.1 | old `minecraftArguments` format, jre-legacy — tests "unresolved" reporting |
| 1.13–1.20 | 1.16.5 | modern arguments, mid-era |
| latest release | 26.3 | modern manifest latest |
| snapshot | 26.4-snapshot-7 | modern manifest latest snapshot |
| old alpha/beta | 1.0 | **NOT in manifest (alpha/beta count=0)**; oldest release available — documents the contradiction |
| Fabric | 1.21.1 + loader 0.14.22 | profile merge test |

## Assumptions documented (decisions)
- Duplicate libraries (Fabric vs vanilla same artifact): vanilla wins; Fabric libs appended only if not already present by `name`. Documented in DESIGN/implementation.
- Version with no natives for linux-arm64: report missing natives as unresolved/required-but-absent; never skip silently, never fail the whole plan.
- All version JSONs assumed modern `arguments` — verified FALSE for pre-1.13; implementation reports `minecraftArguments` as unresolved game args rather than guessing.