# DESIGN — Part B: single Compose launch-plan screen

## Module
- `:app` (Android, minSdk 26, Jetpack Compose + Material 3 Expressive) depends on `:core`.
- Single screen: `LaunchPlanScreen`. No other screens, no navigation, no placeholder data.

## Data shown (from `:core` LaunchPlan)
Stages, each with real count + byte total:
1. Libraries — count + total bytes of library jars
2. Natives — count + total bytes of native jars for linux-arm64 (with missing-natives warning if any)
3. Assets — assetIndex id + object count + totalSize; asset layout label (modern hashed / legacy)
4. Fabric — present/absent; loader version; merged library count; mainClass used

Below stages: a "Dry-run cache verify/download" section:
- Real dry-run into the app cache dir (Context.getCacheDir() / "atlas-cache")
- For each file: verify SHA1 (or report unavailable), show progress bytes/total, state per file
- True progress (bytes read / total), not simulated

## State machine (one purposeful morph)
States: `Idle` → `Running` → `Done` / `Failed`
- Idle: show "Select version" (fixed chosen version, no picker — keep single-screen), no progress
- Running: morph to progress state; per-stage counters animate up; dry-run progress bar per file; show current file being verified
- Done: show "Ready to launch" + totals; morph complete
- Failed: show which file + why (e.g. sha1 mismatch / download error / missing natives); terminal until reset

## Motion
- Own motion tokens (duration, easing) defined in theme, not copied from defaults
- Reduced motion: when `AccessibilityInfo.reducedMotionEnabled`, disable/shorten animators, instant state morph
- State-morph uses `AnimatedVisibility` + `updateTransition` between Idle/Running/Done/Failed

## UI specifics (Material 3 Expressive)
- Expressive typography (display/headline/body/label from Material3 type scale)
- Expressive color (primary/secondary/tertiary containers, on-colors)
- Stage cards: stage name, count chip, byte total chip, status icon
- Progress: `CircularProgressIndicator` for indeterminate (fetching plan), linear per-file during dry-run
- Failed state: outlined error card with file name + reason text

## Boundaries (out of scope)
- No actual persistent download to device storage beyond cache dry-run (dry-run only)
- No auth, no version picker beyond the fixed chosen version, no launch execution
- No extra screens