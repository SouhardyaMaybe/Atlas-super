## PLAN (checkboxes)

- [ ] .agent/ files: PROMPT, GOAL, PLAN, DESIGN, RESEARCH, LOG, FAILURES
- [ ] Research: verify Mojang + Fabric endpoint schemas; check ManifestResolverKit; pick JVM target + record reasoning
- [ ] git init; create public GitHub repo `atlas-super` via `gh` (env token, never print/commit)
- [ ] Scaffold Gradle multi-module: `:core` (Kotlin/JVM), `:app` (Android Compose minSdk 26)
- [ ] Part A: implement Launch Plan resolver (Mojang + Fabric)
- [ ] Part A: JUnit tests w/ recorded fixtures + live smoke test
- [ ] Part B: one Material 3 Expressive Compose screen
- [ ] CI workflow: `./gradlew :core:test`
- [ ] First push: diagnostic only (`./gradlew help`)
- [ ] Up to 8 CI runs; fix first error via `gh run view --log-failed`; 2-strike rule
- [ ] Final report: verified (evidence), not verified, contradictions vs assumptions