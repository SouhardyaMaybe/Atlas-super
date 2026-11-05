package com.atlassuper.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds a LaunchPlan for a Minecraft Java version (+ optional Fabric loader) on a target platform,
 * using live Mojang + Fabric data.
 *
 * Documented policies:
 *   * Duplicate libraries: when Fabric contributes a library whose `name` already exists in the
 *     vanilla library list, the vanilla entry wins (it carries sha1/size); the Fabric duplicate is
 *     skipped. (`FabricInfo.libraries` only contains the newly-added Fabric libs.)
 *   * No natives for target platform: never skipped silently. A library whose natives target the
 *     OS but not the target arch is listed in `missingNatives` with a description.
 *   * Pre-1.13 / legacy versions use `minecraftArguments` (flat string) instead of `arguments`;
 *     these game args are kept as one unresolved entry rather than guessed.
 */
class LaunchPlanner(private val platform: TargetPlatform = TargetPlatform.DEFAULT) {

    /** Fully live: fetch manifest + version JSON (+ Fabric profile) from the network. */
    fun plan(versionId: String, fabricLoaderVersion: String? = null): LaunchPlan {
        val manifest = MojangSource.fetchManifest()
        val versionUrl = MojangSource.findVersionUrl(manifest, versionId)
            ?: error("Version '$versionId' not found in Mojang version manifest")
        val versionJson = MojangSource.fetchVersion(versionUrl)

        var profileJson: JsonElement? = null
        if (fabricLoaderVersion != null) {
            profileJson = FabricSource.fetchProfile(versionId, fabricLoaderVersion)
        }
        return resolve(versionJson, profileJson, fabricLoaderVersion)
    }

    /** Pure parsing: build a plan from already-fetched JSON (used by fixture tests). */
    fun resolve(
        versionJson: JsonElement,
        profileJson: JsonElement? = null,
        fabricLoaderVersion: String? = null,
    ): LaunchPlan {
        val versionId = MojangSource.id(versionJson)
        val clientJar = MojangSource.clientJar(versionJson)
        val javaMajor = MojangSource.javaMajorVersion(versionJson)
        val javaComp = MojangSource.javaComponent(versionJson)
        val ai = MojangSource.assetIndex(versionJson)
        val assetsKey = MojangSource.assetsKey(versionJson)

        val assetLayout = when {
            ai != null -> AssetLayout("hashed", ai, assetsKey)
            assetsKey != null -> AssetLayout("legacy", null, assetsKey)
            else -> AssetLayout("virtual", null, null)
        }

        // ---- gather every downloadable artifact (regular jars + native jars) ----
        val rawEntries = mutableListOf<Triple<String, String?, DownloadEntry>>()
        val ruleCtx = ArgumentResolver.RuleContext(platform)

        for (lib in MojangSource.libraries(versionJson)) {
            val obj = lib as JsonObject
            val name = str(obj["name"]) ?: continue
            if (!ArgumentResolver.applies(arr(obj["rules"]), ruleCtx)) continue

            val main = artifactDownload(obj)
            if (main != null) rawEntries += Triple(name, null, main)

            for ((classifier, nativeDownload) in extractNativeCandidates(obj)) {
                rawEntries += Triple(name, classifier, nativeDownload)
            }
        }

        // De-duplicate by dest path; prefer an entry that has a classifier (native) over one without.
        val byDest = linkedMapOf<String, Triple<String, String?, DownloadEntry>>()
        for (entry in rawEntries) {
            val existing = byDest[entry.third.dest]
            if (existing == null || (entry.second != null && existing.second == null)) {
                byDest[entry.third.dest] = entry
            }
        }

        val allLibs: List<LibraryRef> = byDest.values.map { (name, classifier, download) ->
            LibraryRef(name, download, isNative = classifier != null, nativeClassifier = classifier)
        }

        // ---- select natives matching the target platform ----
        val selectedNatives = mutableListOf<NativeEntry>()
        val missingNatives = mutableListOf<String>()

        // Group native entries by base library name (strip the :classifier suffix) so that
        // e.g. netty-epoll:linux-aarch_64 and netty-epoll:linux-x86_64 are treated as one library.
        fun baseNameOf(name: String): String {
            val suffix = name.substringAfterLast(":")
            return if (suffix.startsWith("natives-") || suffix.contains("-")) name.substringBeforeLast(":") else name
        }

        val nativeEntries = allLibs.filter { it.isNative }
        val byBase = nativeEntries.groupBy { baseNameOf(it.name) }
        for ((baseName, entries) in byBase) {
            val matching = entries.filter { ne ->
                nativeMatchesPlatform(ne.nativeClassifier ?: "native", null, platform)
            }
            if (matching.isNotEmpty()) {
                selectedNatives += matching.map {
                    NativeEntry(it, platform.osName, platform.osArch, it.nativeClassifier ?: "native")
                }
            } else if (entries.any { ne ->
                val (os, _) = parseClassifierOsArch(ne.nativeClassifier ?: "native")
                os == platform.osName
            }) {
                missingNatives += "$baseName (natives for ${platform.osName} exist but none for ${platform.osArch})"
            }
        }

        // ---- classpath: every artifact dest + client jar, ordered, de-duped by dest ----
        val classpath = mutableListOf<String>()
        for (lib in allLibs) {
            if (lib.download != null) classpath += lib.download.dest
        }
        classpath += clientJar.dest

        // ---- arguments ----
        val context = ResolutionContext(
            versionId = versionId,
            versionType = MojangSource.type(versionJson),
            assetsIndexName = ai?.id,
        )
        val gameArgs = buildArgumentList(
            arr(MojangSource.arguments(versionJson)?.get("game")),
            MojangSource.minecraftArguments(versionJson),
            context, ruleCtx)
        val jvmArgs = buildArgumentList(
            (arr(MojangSource.arguments(versionJson)?.get("jvm")) ?: emptyList<JsonElement>()) +
                (arr(MojangSource.arguments(versionJson)?.get("default-user-jvm")) ?: emptyList<JsonElement>()),
            null, context, ruleCtx)

        // ---- Fabric merge ----
        var finalMainClass = MojangSource.mainClass(versionJson)
        var finalJvmArgs = jvmArgs
        var finalGameArgs = gameArgs
        var allLibsFinal: List<LibraryRef> = allLibs
        var fabric: FabricInfo? = null

        if (fabricLoaderVersion != null) {
            val profile = profileJson ?: error("Fabric requested but no profile JSON supplied")
            finalMainClass = FabricSource.fabricMainClass(profile) ?: finalMainClass
            val fabricJvm = FabricSource.fabricJvmArgs(profile)
            val fabricGame = FabricSource.fabricGameArgs(profile)

            val vanillaNames = allLibs.map { it.name }.toSet()
            val newFabricLibs = mutableListOf<LibraryRef>()
            for (flib in FabricSource.fabricLibraries(profile)) {
                val fname = FabricSource.libName(flib)
                if (fname in vanillaNames) continue // vanilla wins (has sha1/size)
                val fUrl = FabricSource.libUrl(flib) ?: continue
                val (group, artifact, ver) = splitCoord(fname)
                val groupPath = group.replace(".", "/")
                val nativeUrl = "$fUrl/$groupPath/$artifact/$ver/$artifact-$ver.jar"
                val fDest = "libraries/fabric/$groupPath/$artifact-$ver.jar"
                newFabricLibs += LibraryRef(
                    fname,
                    DownloadEntry(nativeUrl, null, null, fDest),
                    isNative = false,
                    nativeClassifier = null,
                    repoBase = fUrl,
                )
            }
            allLibsFinal = allLibsFinal + newFabricLibs
            finalJvmArgs = ArgumentList(jvmArgs.args + fabricJvm, jvmArgs.unresolved)
            finalGameArgs = ArgumentList(gameArgs.args + fabricGame, gameArgs.unresolved)
            fabric = FabricInfo(
                loaderVersion = fabricLoaderVersion,
                gameVersion = versionId,
                mainClass = finalMainClass,
                libraries = newFabricLibs,
                jvmArgs = fabricJvm,
                gameArgs = fabricGame,
            )
        }

        return LaunchPlan(
            versionId = versionId,
            versionType = MojangSource.type(versionJson),
            mainClass = finalMainClass,
            javaMajorVersion = javaMajor,
            javaComponent = javaComp,
            clientJar = clientJar,
            libraries = allLibsFinal,
            natives = selectedNatives,
            missingNatives = missingNatives,
            assetLayout = assetLayout,
            gameArgs = finalGameArgs,
            jvmArgs = finalJvmArgs,
            classpath = classpath.distinct(),
            fabric = fabric,
        )
    }

    private fun str(el: JsonElement?): String? = (el as? JsonPrimitive)?.content
    private fun obj(el: JsonElement?): JsonObject? = el as? JsonObject
    private fun arr(el: JsonElement?): JsonArray? = el as? JsonArray

    // ---------- helpers ----------

    private fun artifactDownload(lib: JsonObject): DownloadEntry? {
        val artifact = obj(obj(obj(lib["downloads"])?.get("artifact"))) ?: return null
        val url = str(artifact["url"]) ?: return null
        val sha1 = str(artifact["sha1"])
        val size = str(artifact["size"])?.toLong()
        val path = str(artifact["path"]) ?: url
        return DownloadEntry(url, sha1, size, "libraries/$path")
    }

    /**
     * Native downloads declared via `downloads.classifiers` + `natives` map (legacy/modern map style),
     * or a `:classifier` name suffix (modern suffix style). Returns (classifierName, DownloadEntry).
     */
    private fun extractNativeCandidates(lib: JsonObject): List<Pair<String, DownloadEntry>> {
        val classifiers = obj(obj(lib["downloads"])?.get("classifiers"))
        if (classifiers != null) {
            return classifiers.entries.mapNotNull { (classifierName, entryJson) ->
                val entryObj = entryJson as JsonObject
                val url = str(entryObj["url"]) ?: return@mapNotNull null
                val path = str(entryObj["path"]) ?: url
                Pair(classifierName, DownloadEntry(
                    url,
                    str(entryObj["sha1"]),
                    str(entryObj["size"])?.toLong(),
                    "libraries/$path",
                ))
            }
        }

        // Modern classifier suffix on the library name, e.g. "...:natives-linux" or "...:linux-aarch_64"
        val name = str(lib["name"]) ?: return emptyList()
        val suffix = name.substringAfterLast(":")
        val looksLikeClassifier = suffix.startsWith("natives-") || suffix.contains("-")
        if (!looksLikeClassifier) return emptyList()
        val artifactObj = obj(obj(lib["downloads"])?.get("artifact")) ?: return emptyList()
        val url = str(artifactObj["url"]) ?: return emptyList()
        val path = str(artifactObj["path"]) ?: url
        return listOf(suffix to DownloadEntry(
            url,
            str(artifactObj["sha1"]),
            str(artifactObj["size"])?.toLong(),
            "libraries/$path",
        ))
    }

    private fun buildArgumentList(
        elements: Iterable<JsonElement>?,
        legacyString: String?,
        context: ResolutionContext,
        ruleCtx: ArgumentResolver.RuleContext,
    ): ArgumentList {
        if (legacyString != null) {
            return ArgumentList(
                args = listOf(legacyString),
                unresolved = listOf(UnresolvedPlaceholder("minecraftArguments",
                    "legacy flat format (pre-1.13); placeholders not individually resolved")),
            )
        }
        val args = mutableListOf<String>()
        val unresolved = mutableListOf<UnresolvedPlaceholder>()
        for (el in elements ?: emptyList<JsonElement>()) {
            when (el) {
                is JsonObject -> {
                    if (!ArgumentResolver.applies(arr(el["rules"]), ruleCtx)) continue
                    val value = el["value"]
                    if (value is JsonArray) {
                        for (v in value) {
                            val s = str(v) ?: continue
                            val (resolved, unr) = ArgumentResolver.resolveString(s, context)
                            args += resolved
                            unresolved += unr
                        }
                    } else {
                        val s = str(value) ?: continue
                        val (resolved, unr) = ArgumentResolver.resolveString(s, context)
                        args += resolved
                        unresolved += unr
                    }
                }
                else -> {
                    // Plain string argument (JsonLiteral): no rules, use directly.
                    val s = str(el) ?: continue
                    val (resolved, unr) = ArgumentResolver.resolveString(s, context)
                    args += resolved
                    unresolved += unr
                }
            }
        }
        return ArgumentList(args, unresolved.distinctBy { it.placeholder })
    }

    private fun splitCoord(coord: String): Triple<String, String, String> {
        val parts = coord.split(":", limit = 3)
        return Triple(parts[0], parts[1], if (parts.size > 2) parts[2] else "")
    }
}
