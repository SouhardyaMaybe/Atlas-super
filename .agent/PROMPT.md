# TEST TASK: "Launch Plan", the core of a Minecraft launcher, proven by tests

Work ONLY in this new scratch repo: Atlas-super. Never touch my other repos. Obey AGENTS.md. No local builds: all builds run in GitHub Actions.

## Goal
A pure Kotlin/JVM module `:core` that turns a Minecraft Java version id (plus an optional Fabric loader) into a complete, verified LAUNCH PLAN, using LIVE Mojang and Fabric data. Then one small Compose screen shows it.

## Part A: `:core` (the hard part)
Input: version id, optional Fabric loader version, target platform linux-arm64 (what Android launchers run on). Output:
1. Download list: client jar, libraries, native libraries, asset objects. Each with url, SHA1, size, destination path.
2. Classpath (ordered, no duplicate artifacts) and main class.
3. Complete JVM + game argument lists with every ${placeholder} resolved, or explicitly reported as unresolved.
4. Required Java major version.
5. Asset layout (modern hashed layout or legacy/virtual, whichever the version needs).
Inspect the real JSON from the official endpoints (find and cite them). Don't guess schemas.
Assume all version JSONs use the same modern `arguments` format. (Client assumption: verify it, don't trust it.)
Fabric: merge the loader's profile into the vanilla plan correctly. Decide and document how conflicting duplicate libraries are resolved.
Decide and document what happens when a version has no natives for the target platform. Never skip silently.
Don't use "ManifestResolverKit" unless you verify it exists, is maintained and fits. Report what you found.
Tests (JUnit): recorded real fixtures committed with their fetch date, plus ONE live smoke test. Cover at least: one pre-1.13 version, one 1.13-1.20 version, the latest release, one snapshot, one old alpha/beta, and Fabric on a version of your choice. CI must run `./gradlew :core:test` and show the results.

## Part B: one screen
Shows the resolved plan for a chosen version as stages (Libraries, Natives, Assets, Fabric) with real counts and byte totals, plus a REAL dry-run of verifying/downloading into the app cache with true progress. Material 3 Expressive only. Write `.agent/DESIGN.md` first. Own motion tokens, one purposeful state-morph (idle -> running -> done/failed), reduced motion respected. States: running, failed (which file, why), done. No placeholder data, no extra screens or features.

## Process
1. Ask me up to 3 questions in ONE message about anything truly ambiguous, or state your assumptions. Then wait.
2. `.agent/` files: PROMPT, GOAL (in/out of scope), PLAN, DESIGN, RESEARCH (URLs), LOG, FAILURES.
3. First push = diagnostic workflow only (`./gradlew help`). Max 8 full CI runs. Read the FIRST error with `gh run view --log-failed`. The 2-strike rule applies.
4. Final report: what you verified (with evidence), what you didn't, and what in the real data contradicted the assumptions I gave you.