package com.atlassuper.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.JsonObject

class LaunchPlannerTest {

    private val platform = TargetPlatform.DEFAULT // linux-arm64
    private val planner = LaunchPlanner(platform)

    // ---------- fixture loading ----------

    private fun fixtureText(name: String): String {
        val stream: InputStream = javaClass.getResourceAsStream("/fixtures/$name")
            ?: error("Fixture not found: $name")
        return String(stream.readAllBytes(), StandardCharsets.UTF_8)
    }

    private fun parseFixture(name: String): kotlinx.serialization.json.JsonElement {
        return MojangSource.json.parseToJsonElement(fixtureText(name))
    }

    // ---------- pre-1.13: legacy minecraftArguments ----------

    @Test
    fun pre_1_13_1_8_1_resolves_legacy_minecraft_arguments() {
        val plan = planner.resolve(parseFixture("version_1.8.1.json"))

        assertEquals("1.8.1", plan.versionId)
        assertEquals("release", plan.versionType)
        assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        assertEquals(8, plan.javaMajorVersion)
        assertEquals("jre-legacy", plan.javaComponent)
        assertNotNull(plan.clientJar.sha1)
        assertTrue(plan.clientJar.size!! > 0)
        assertTrue(plan.clientJar.url.contains("client.jar"))

        // Legacy format: game args kept as one unresolved flat string (not guessed)
        assertEquals(1, plan.gameArgs.args.size)
        assertTrue(plan.gameArgs.args.first().startsWith("--username"))
        assertEquals(1, plan.gameArgs.unresolved.size)
        assertEquals("minecraftArguments", plan.gameArgs.unresolved.first().placeholder)

        // Asset layout is modern hashed (assetIndex present since 1.6)
        assertEquals("hashed", plan.assetLayout.kind)
        assertNotNull(plan.assetLayout.assetIndex)
        assertEquals("1.8", plan.assetLayout.assetIndex!!.id)

        // Libraries + classpath resolved, de-duped
        assertTrue(plan.libraries.isNotEmpty())
        assertTrue(plan.classpath.isNotEmpty())
        assertTrue(plan.classpath.any { it.endsWith(".jar") })
        assertEquals(plan.classpath.distinct().size, plan.classpath.size) // no dupes
    }

    // ---------- 1.13-1.20: modern arguments, Java 8 ----------

    @Test
    fun v1_13_to_1_20_1_16_5_resolves_modern_arguments() {
        val plan = planner.resolve(parseFixture("version_1.16.5.json"))

        assertEquals("1.16.5", plan.versionId)
        assertEquals(8, plan.javaMajorVersion)
        assertEquals("jre-legacy", plan.javaComponent)
        assertEquals("net.minecraft.client.main.Main", plan.mainClass)

        // Modern arguments: version_name resolved
        assertTrue(plan.gameArgs.args.any { it.contains("1.16.5") })

        // Assets hashed
        assertEquals("hashed", plan.assetLayout.kind)
        assertEquals("1.16", plan.assetLayout.assetIndex!!.id)

        // Classpath de-duped, ordered
        assertEquals(plan.classpath.distinct().size, plan.classpath.size)

        // Download entries carry sha1 + size
        plan.libraries.filter { it.download != null }.forEach { lib ->
            assertNotNull(lib.download!!.sha1, "sha1 missing for ${lib.name}")
            assertNotNull(lib.download!!.size, "size missing for ${lib.name}")
        }
    }

    // ---------- latest release: Java 25 ----------

    @Test
    fun latest_release_26_3_resolves_with_java_25() {
        val plan = planner.resolve(parseFixture("version_26.3.json"))

        assertEquals("26.3", plan.versionId)
        assertEquals("release", plan.versionType)
        assertEquals(25, plan.javaMajorVersion)
        assertEquals("java-runtime-epsilon", plan.javaComponent)
        assertEquals("net.minecraft.client.main.Main", plan.mainClass)

        // Natives selected for linux-arm64 (netty :linux-aarch_64 + arch-neutral natives-linux)
        assertTrue(plan.natives.isNotEmpty(), "expected natives for linux-arm64")
        assertTrue(plan.natives.any { it.classifier == "linux-aarch_64" })

        // Client jar
        assertNotNull(plan.clientJar.sha1)
        assertTrue(plan.clientJar.size!! > 0)

        // No missing natives (arm64 covered)
        assertTrue(plan.missingNatives.isEmpty(), "unexpected missing natives: ${plan.missingNatives}")

        // Arguments resolved (classpath present)
        assertTrue(plan.jvmArgs.args.any { it.contains("-cp") || it.contains("classpath") })
    }

    // ---------- snapshot ----------

    @Test
    fun snapshot_26_4_snapshot_7_resolves() {
        val plan = planner.resolve(parseFixture("version_26.4-snapshot-7.json"))

        assertEquals("26.4-snapshot-7", plan.versionId)
        assertEquals("snapshot", plan.versionType)
        assertEquals(25, plan.javaMajorVersion)
        assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        assertNotNull(plan.clientJar.sha1)
        assertTrue(plan.libraries.isNotEmpty())
        assertTrue(plan.classpath.isNotEmpty())
    }

    // ---------- old 1.0 (oldest release; manifest has no alpha/beta ids) ----------

    @Test
    fun old_1_0_oldest_release() {
        val plan = planner.resolve(parseFixture("version_1.0.json"))

        assertEquals("1.0", plan.versionId)
        assertEquals("net.minecraft.launchwrapper.Launch", plan.mainClass) // differs from modern Main
        assertEquals(8, plan.javaMajorVersion)
        assertEquals("jre-legacy", plan.javaComponent)
        assertEquals(1, plan.gameArgs.unresolved.size) // legacy minecraftArguments
        assertEquals("hashed", plan.assetLayout.kind) // pre-1.6 asset index "pre-1.6" still hashed layout
    }

    // ---------- Fabric merge ----------

    @Test
    fun fabric_loader_0_14_22_merges_into_the_plan() {
        val modern = parseFixture("version_26.3.json")
        val profile = MojangSource.json.parseToJsonElement(
            fixtureText("fabric_profile_1.21.1_0.14.22.json"))

        val plan = planner.resolve(modern, profile, "0.14.22")

        assertNotNull(plan.fabric)
        assertEquals("0.14.22", plan.fabric!!.loaderVersion)
        // Fabric replaces the main class
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", plan.mainClass)
        assertNotEquals("net.minecraft.client.main.Main", plan.mainClass)
        // Fabric libraries added (sha1/size unresolved)
        assertTrue(plan.fabric!!.libraries.isNotEmpty())
        plan.fabric!!.libraries.forEach { lib ->
            assertNull(lib.download!!.sha1, "Fabric lib ${lib.name} should have unresolved sha1")
            assertNull(lib.download!!.size, "Fabric lib ${lib.name} should have unresolved size")
        }
        // Fabric JVM args merged
        assertTrue(plan.jvmArgs.args.any { it.contains("FabricMcEmu") })
    }

    // ---------- placeholder resolution unit checks ----------

    @Test
    fun resolves_known_placeholders_and_reports_unknown_ones() {
        val ctx = ResolutionContext("1.16.5", "release", "1.16")
        val (resolved, unresolved) = ArgumentResolver.resolveString(
            "--version \${version_name} --auth \${auth_uuid} --weird \${unknown_placeholder}", ctx)
        assertEquals("--version 1.16.5 --auth \${auth_uuid} --weird \${unknown_placeholder}", resolved)
        assertEquals(2, unresolved.size)
        assertTrue(unresolved.any { it.placeholder == "auth_uuid" })
        assertTrue(unresolved.any { it.placeholder == "unknown_placeholder" })
    }

    @Test
    fun os_rule_selects_linux_natives_and_excludes_osx() {
        val ctx = ArgumentResolver.RuleContext(platform)
        assertTrue(ArgumentResolver.applies(
            listOf(parseRule("""{"action":"allow","os":{"name":"linux"}}""")), ctx))
        assertFalse(ArgumentResolver.applies(
            listOf(parseRule("""{"action":"allow","os":{"name":"osx"}}""")), ctx))
    }

    private fun parseRule(s: String): kotlinx.serialization.json.JsonElement =
        MojangSource.json.parseToJsonElement(s)

    // ---------- one live smoke test (network) ----------

    @Test
    fun live_smoke_test_plan_latest_release() {
        val manifest = MojangSource.fetchManifest()
        val latestRelease = (manifest as JsonObject)["latest"]?.let { (it as JsonObject)["release"] } as? kotlinx.serialization.json.JsonPrimitive
            ?: error("manifest missing latest.release")
        val plan = planner.plan(latestRelease.content)

        assertEquals(latestRelease.content, plan.versionId)
        assertNotNull(plan.mainClass)
        assertTrue(plan.javaMajorVersion > 0)
        assertNotNull(plan.clientJar.sha1)
        assertTrue(plan.clientJar.size!! > 0)
        assertFalse(plan.classpath.isEmpty())
    }
}
