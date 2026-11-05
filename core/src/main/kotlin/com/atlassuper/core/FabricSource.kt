package com.atlassuper.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Fabric loader metadata.
 *
 * Endpoints (verified live):
 *   https://meta.fabricmc.net/v2/versions/loader                                    -> loader version list
 *   https://meta.fabricmc.net/v2/versions/loader/{gameVersion}/{loaderVersion}/profile/json
 *   https://maven.fabricmc.net/...                                                    (Fabric library downloads)
 */
object FabricSource {
    val json = Json { ignoreUnknownKeys = true }
    private val emptyArray: JsonArray = json.parseToJsonElement("[]") as JsonArray

    private const val LOADER_URL = "https://meta.fabricmc.net/v2/versions/loader"

    /** List of available loader versions. */
    fun fetchLoaders(): JsonElement = Http.get(LOADER_URL).let { json.parseToJsonElement(it) }

    /** The merged loader profile for a game version + loader version. */
    fun fetchProfile(gameVersion: String, loaderVersion: String): JsonElement {
        val url = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/$loaderVersion/profile/json"
        return Http.get(url).let { json.parseToJsonElement(it) }
    }

    private fun str(el: JsonElement?): String? = (el as? JsonPrimitive)?.content
    private fun obj(el: JsonElement?): JsonObject? = el as? JsonObject
    private fun arr(el: JsonElement?): JsonArray? = el as? JsonArray

    fun inheritsFrom(profile: JsonElement): String? = str((profile as JsonObject)["inheritsFrom"])

    fun fabricMainClass(profile: JsonElement): String? = str((profile as JsonObject)["mainClass"])

    fun fabricJvmArgs(profile: JsonElement): List<String> {
        val args = arr(
            (profile as JsonObject)["arguments"]?.let { (it as JsonObject)["jvm"] }
        )
        return args?.mapNotNull { str(it) } ?: emptyList()
    }

    fun fabricGameArgs(profile: JsonElement): List<String> {
        val args = arr(
            (profile as JsonObject)["arguments"]?.let { (it as JsonObject)["game"] }
        )
        return args?.mapNotNull { str(it) } ?: emptyList()
    }

    /** Fabric libraries: each {name, url} (no sha1/size in the profile). */
    fun fabricLibraries(profile: JsonElement): JsonArray =
        arr((profile as JsonObject)["libraries"]) ?: emptyArray

    fun libName(lib: JsonElement): String = str((lib as JsonObject)["name"])!!
    fun libUrl(lib: JsonElement): String? = str((lib as JsonObject)["url"])
}
