package com.atlassuper.core

/** A fully resolved launch plan for a Minecraft Java version (+ optional Fabric loader) on a target platform. */
data class LaunchPlan(
    /** The Minecraft version id this plan resolves (e.g. "1.16.5", "26.3"). */
    val versionId: String,
    /** Version type (release / snapshot / ...). */
    val versionType: String,
    /** Main class to launch (vanilla, or Fabric's KnotClient when Fabric is applied). */
    val mainClass: String,
    /** Required Java major version (from javaVersion.majorVersion). */
    val javaMajorVersion: Int,
    /** javaVersion.component (e.g. java-runtime-epsilon / jre-legacy). */
    val javaComponent: String?,
    /** The client jar to download. */
    val clientJar: DownloadEntry,
    /** All libraries (regular + natives) ordered for the classpath, de-duplicated by artifact name. */
    val libraries: List<LibraryRef>,
    /** Native libraries selected for the target platform. */
    val natives: List<NativeEntry>,
    /** Native libraries the version requires for the target platform but does not provide (never silently skipped). */
    val missingNatives: List<String>,
    /** Asset layout the version needs. */
    val assetLayout: AssetLayout,
    /** Game arguments with placeholders resolved or reported unresolved. */
    val gameArgs: ArgumentList,
    /** JVM arguments with placeholders resolved or reported unresolved. */
    val jvmArgs: ArgumentList,
    /** Ordered, de-duplicated classpath destination paths. */
    val classpath: List<String>,
    /** Fabric merge info, or null when no loader was requested. */
    val fabric: FabricInfo?,
)

/** A single downloadable artifact. */
data class DownloadEntry(
    val url: String,
    /** SHA-1 of the artifact, or null when the source does not provide one (e.g. Fabric libraries). */
    val sha1: String?,
    val size: Long?,
    /** Destination path relative to the install root. */
    val dest: String,
)

/** A library artifact (regular jar or native). */
data class LibraryRef(
    /** Maven coordinate "group:artifact:version". */
    val name: String,
    /** Where to download it (null for Fabric libs that carry no artifact metadata). */
    val download: DownloadEntry?,
    /** True when this entry is a native library. */
    val isNative: Boolean,
    /** Native classifier (e.g. "natives-linux", "linux-aarch_64") when applicable. */
    val nativeClassifier: String?,
    /** Source repository base URL when not libraries.minecraft.net (e.g. maven.fabricmc.net). */
    val repoBase: String? = null,
)

/** A native library selected for the target platform. */
data class NativeEntry(
    val library: LibraryRef,
    val os: String,
    val arch: String?,
    val classifier: String,
)

/** How assets are stored for this version. */
data class AssetLayout(
    /** "hashed" (modern assetIndex) / "legacy" / "virtual". */
    val kind: String,
    /** The asset index object (null for legacy/virtual). */
    val assetIndex: AssetIndex?,
    /** The `assets` key value (asset index name / virtual dir). */
    val assetsKey: String?,
)

/** The asset index object describing where hashed asset objects live. */
data class AssetIndex(
    val id: String,
    val sha1: String,
    val size: Long,
    val totalSize: Long,
    val url: String,
)

/** An argument list (JVM or game) with placeholders resolved or reported unresolved. */
data class ArgumentList(
    /** Final argument strings (placeholders substituted where a value is known). */
    val args: List<String>,
    /** Placeholders that could not be resolved, with a reason. */
    val unresolved: List<UnresolvedPlaceholder>,
)

/** A placeholder that could not be resolved. */
data class UnresolvedPlaceholder(
    val placeholder: String,
    /** Why it is unresolved (e.g. requires auth, runtime value, legacy format). */
    val reason: String,
)

/** Result of merging a Fabric loader profile into the vanilla plan. */
data class FabricInfo(
    val loaderVersion: String,
    val gameVersion: String,
    val mainClass: String,
    /** Fabric libraries merged into the plan (sha1/size unresolved). */
    val libraries: List<LibraryRef>,
    /** JVM arguments contributed by the loader. */
    val jvmArgs: List<String>,
    /** Game arguments contributed by the loader (usually empty). */
    val gameArgs: List<String>,
)
