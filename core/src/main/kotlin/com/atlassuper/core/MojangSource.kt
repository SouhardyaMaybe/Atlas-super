package com.atlassuper.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads Mojang's version manifest + per-version JSON.
 *
 * Endpoints (verified live):
 *   https://launchermeta.mojang.com/mc/game/version_manifest.json
 *   https://piston-meta.mojang.com/v1/packages/{hash}/{version}.json   (from a manifest entry)
 *   https://libraries.minecraft.net/...                                  (library downloads)
 *   https://piston-data.mojang.com/v1/objects/{sha1}/...                (client jar / assets, modern)
 *   https://launcher.mojang.com/v1/objects/{sha1}/...                   (client jar / assets, legacy)
 */
object MojangSource {
    val json = Json { ignoreUnknownKeys = true }
    private val emptyLibraries: JsonArray = json.parseToJsonElement("[]").jsonArray

    private const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"

    /** Fetch the version manifest. */
    fun fetchManifest(): JsonElement = Http.get(MANIFEST_URL).let { json.parseToJsonElement(it) }

    /** Find a version entry in the manifest by id. Returns the version JSON URL. */
    fun findVersionUrl(manifest: JsonElement, versionId: String): String? {
        val versions = manifest.jsonObject["versions"]?.jsonArray ?: return null
        return versions.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == versionId }
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive?.content
    }

    /** Fetch and parse a single version JSON. */
    fun fetchVersion(versionUrl: String): JsonElement = Http.get(versionUrl).let { json.parseToJsonElement(it) }

    // ---------- convenient accessors ----------

    fun id(version: JsonElement): String = version.jsonObject["id"]!!.jsonPrimitive.content
    fun type(version: JsonElement): String = version.jsonObject["type"]!!.jsonPrimitive.content

    fun javaMajorVersion(version: JsonElement): Int {
        return version.jsonObject["javaVersion"]?.jsonObject
            ?.get("majorVersion")?.jsonPrimitive?.content?.toInt() ?: -1
    }

    fun javaComponent(version: JsonElement): String? =
        version.jsonObject["javaVersion"]?.jsonObject?.get("component")?.jsonPrimitive?.content

    fun mainClass(version: JsonElement): String =
        version.jsonObject["mainClass"]!!.jsonPrimitive.content

    fun assetIndex(version: JsonElement): AssetIndex? {
        val ai = version.jsonObject["assetIndex"] ?: return null
        val obj = ai.jsonObject
        return AssetIndex(
            id = obj["id"]!!.jsonPrimitive.content,
            sha1 = obj["sha1"]!!.jsonPrimitive.content,
            size = obj["size"]!!.jsonPrimitive.content.toLong(),
            totalSize = (obj["totalSize"]?.jsonPrimitive?.content ?: "0").toLong(),
            url = obj["url"]!!.jsonPrimitive.content,
        )
    }

    fun assetsKey(version: JsonElement): String? =
        version.jsonObject["assets"]?.jsonPrimitive?.content

    fun clientJar(version: JsonElement): DownloadEntry {
        val c = version.jsonObject["downloads"]!!.jsonObject["client"]!!.jsonObject
        return DownloadEntry(
            url = c["url"]!!.jsonPrimitive.content,
            sha1 = c["sha1"]!!.jsonPrimitive.content,
            size = c["size"]!!.jsonPrimitive.content.toLong(),
            dest = "versions/${id(version)}/${id(version)}.jar",
        )
    }

    /** The raw `arguments` block, or null for legacy versions that use `minecraftArguments`. */
    fun arguments(version: JsonElement): JsonObject? =
        version.jsonObject["arguments"]?.jsonObject

    /** Legacy flat minecraftArguments string (pre-1.13 style). */
    fun minecraftArguments(version: JsonElement): String? =
        version.jsonObject["minecraftArguments"]?.jsonPrimitive?.content

    /** The libraries array (may be empty for very old versions). */
    fun libraries(version: JsonElement): JsonArray =
        version.jsonObject["libraries"]?.jsonArray ?: emptyLibraries

    /** Download URL for a library artifact (libraries.minecraft.net by default). */
    fun libraryUrl(lib: JsonObject): String? =
        lib["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("url")?.jsonPrimitive?.content

    /** SHA-1 of a library artifact. */
    fun librarySha1(lib: JsonObject): String? =
        lib["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("sha1")?.jsonPrimitive?.content

    /** Size of a library artifact. */
    fun librarySize(lib: JsonObject): Long? =
        lib["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("size")?.jsonPrimitive?.content?.toLong()

    /** Artifact path (used to build dest). */
    fun libraryPath(lib: JsonObject): String? =
        lib["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("path")?.jsonPrimitive?.content
}
