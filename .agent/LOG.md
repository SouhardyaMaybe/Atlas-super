## LOG

2026-11-05: Started. Verified live schemas:
- Mojang manifest https://launchermeta.mojang.com/mc/game/version_manifest.json (ships 26.x; 1.21.x/1.20.x/.../1.0 present; NO alpha/beta ids).
- Version JSON: 26.3 + 26.4-snapshot-7 use modern `arguments`, java-runtime-epsilon, majorVersion 25, `:os-arch` native classifiers. 1.16.5 uses modern `arguments` but jre-legacy/Java 8 + old natives map (natives-linux = x86_64 only → no arm64). 1.8.1 + 1.0 use LEGACY `minecraftArguments` (flat string), NO `arguments` key, jre-legacy/Java 8, old natives map, mainClass differs (1.0 = launchwrapper.Launch). → Contradicts "all modern arguments" assumption.
- Fabric profile https://meta.fabricmc.net/v2/versions/loader/{game}/{loader}/profile/json: inheritsFrom, mainClass=KnotClient, arguments{game,jvm}, libraries{name,url} only (no sha1/size). → Fabric libs have unresolved integrity.
- ManifestResolverKit: not found (web search empty). Not used.
- Fixture JSONs committed to core/src/test/resources/fixtures/ with fetch-date.txt.
- JVM target: compile to JVM 17 bytecode, no toolchain pin (local JDK 21, CI JDK 17). Recorded in RESEARCH.md.
- :core implementation complete (LaunchPlan, Platform, ArgumentResolver, MojangSource, FabricSource, Http, LaunchPlanner).
- ./gradlew :core:test passes locally: 9/9 (incl. live smoke test against Mojang manifest).
- CI workflow added (.github/workflows/ci.yml: JDK 17, ./gradlew :core:test).
- Repo: git init'd, public repo SouhardyaMaybe/Atlas-super created, first push = diagnostic scaffold. Pushed to main.
NEXT: push implementation + CI (CI run #1), verify :core:test passes in GitHub Actions. Then add :app Compose screen (Part B).