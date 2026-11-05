## GOAL
One-paragraph: Build an Android app `atlas-super` with a pure Kotlin/JVM `:core` module that resolves a Minecraft Java version id (+ optional Fabric loader) into a complete, verified LAUNCH PLAN using live Mojang + Fabric data, and show it on one Material 3 Expressive Compose screen.

## IN SCOPE
- `:core` pure Kotlin/JVM module (JVM target chosen by me, recorded in RESEARCH.md)
- `:app` Android module (Jetpack Compose, minSdk 26) depending on `:core`
- Launch plan: download list (client jar, libraries, natives, asset objects w/ url/sha1/size/dest), ordered de-duped classpath + main class, resolved JVM + game args (or unresolved report), required Java major version, asset layout
- Fabric loader profile merge with documented duplicate-resolution + no-silent-skip rules for missing natives
- Tests: JUnit, recorded real fixtures w/ fetch date + ONE live smoke test; cover pre-1.13, 1.13-1.20, latest release, snapshot, old alpha/beta, Fabric
- CI: `./gradlew :core:test`, results shown
- Part B: one Compose screen, stages Libraries/Natives/Assets/Fabric with counts+byte totals, real dry-run verify/download into app cache w/ true progress, Material 3 Expressive, motion tokens, idle->running->done/failed state morph, reduced motion, no placeholder data, no extra screens
- git init + NEW public GitHub repo `atlas-super` via `gh` using env token (never print/commit it)
- First push = diagnostic workflow only (`./gradlew help`); max 8 full CI runs; 2-strike rule; read first error via `gh run view --log-failed`

## OUT OF SCOPE
- No local builds (all builds in GitHub Actions)
- No touching other repos
- No ManifestResolverKit unless verified (report findings)
- No extra screens/features beyond the one Compose screen
- No persisting the GitHub token anywhere