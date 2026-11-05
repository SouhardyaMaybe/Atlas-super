package com.atlassuper.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Values we can resolve without user auth or runtime launcher state. */
data class ResolutionContext(
    val versionId: String,
    val versionType: String,
    val assetsIndexName: String?,
    val gameDirectory: String = "game",
    val assetsRoot: String = "assets",
    val nativesDirectory: String = "natives",
    val launcherName: String = "atlas-super",
    val launcherVersion: String = "0.1.0",
)

object ArgumentResolver {
    private val placeholderRegex = "\\$\\{([^}]+)\\}".toRegex()

    /** Placeholders resolvable from context. `${classpath}` is handled by the caller (splices the classpath list). */

    /** Resolve placeholders in a single string. Returns (resolvedString, unresolvedPlaceholdersFound). */
    fun resolveString(template: String?, context: ResolutionContext): Pair<String, List<UnresolvedPlaceholder>> {
        if (template == null) return "" to emptyList()
        val unresolved = mutableListOf<UnresolvedPlaceholder>()
        val result = placeholderRegex.replace(template) { match ->
            val key = match.groupValues[1]
            when (key) {
                "classpath" -> "${match.value}" // caller splices the real classpath
                "version_name" -> context.versionId
                "version_type" -> context.versionType
                "assets_index_name" -> context.assetsIndexName ?: ""
                "game_directory" -> context.gameDirectory
                "assets_root" -> context.assetsRoot
                "natives_directory" -> context.nativesDirectory
                "launcher_name" -> context.launcherName
                "launcher_version" -> context.launcherVersion
                else -> {
                    unresolved += UnresolvedPlaceholder(key, unresolvedReason(key))
                    match.value // leave as-is so it is explicitly visible
                }
            }
        }
        return result to unresolved
    }

    /** Extract every ${...} placeholder mentioned in a template (for reporting). */
    fun placeholdersIn(template: String?): List<String> {
        if (template == null) return emptyList()
        return placeholderRegex.findAll(template).map { it.groupValues[1] }.distinct().toList()
    }

    private fun unresolvedReason(key: String): String = when {
        key.startsWith("auth_") -> "requires authentication (not available in plan)"
        key in listOf("clientid", "user_properties", "user_type") -> "requires launcher/session state"
        key.startsWith("resolution_") || key.startsWith("quickPlay") -> "requires runtime window/session state"
        key == "path" -> "log4j config path injected at launch time"
        else -> "no known value for this placeholder"
    }

    /** Primitive string content of a JsonElement, or null. */
    private fun str(el: JsonElement?): String? = (el as? JsonPrimitive)?.content

    /** Object view of a JsonElement, or null. */
    private fun obj(el: JsonElement?): JsonObject? = el as? JsonObject

    /** Apply version-argument rules (allow/disallow by os / features) to decide whether an element is active. */
    fun applies(rules: List<JsonElement>?, context: RuleContext): Boolean {
        if (rules.isNullOrEmpty()) return true
        var matched = false
        var allowed = false // rules present: default-deny unless a matching rule says allow
        for (rule in rules) {
            val ruleObj = rule as JsonObject
            val action = str(ruleObj["action"]) ?: "allow"
            val ruleMatches = when {
                ruleObj.containsKey("os") -> osMatches(ruleObj["os"] as JsonObject, context)
                ruleObj.containsKey("features") -> featuresMatches(ruleObj["features"] as JsonObject, context)
                else -> true
            }
            if (ruleMatches) {
                matched = true
                allowed = (action == "allow")
            }
        }
        return matched && allowed
    }

    /** Evaluation context for rule matching: target platform + disabled feature set. */
    data class RuleContext(
        val platform: TargetPlatform,
        /** Feature flags that are false (e.g. is_demo_user=false, has_custom_resolution=false). */
        val disabledFeatures: Set<String> = emptySet(),
    )

    private fun osMatches(osObj: JsonObject, ctx: RuleContext): Boolean {
        val name = str(osObj["name"]) ?: return false
        if (name != ctx.platform.osName) return false
        val arch = str(osObj["arch"])
        if (arch != null && arch != ctx.platform.osArch) return false
        // os.version / versionRange refer to the real host OS version, unknown at plan time.
        if (osObj.containsKey("version") || osObj.containsKey("versionRange")) return false
        return true
    }

    private fun featuresMatches(featObj: JsonObject, ctx: RuleContext): Boolean {
        // A features rule matches only if ALL listed features are enabled (not disabled).
        return featObj.entries.all { (key, _) -> key !in ctx.disabledFeatures }
    }
}
