package com.atlassuper.core

/** The target platform we build a launch plan for. Android launchers run on linux-arm64. */
data class TargetPlatform(
    val osName: String = "linux",
    val osArch: String = "aarch_64", // Minecraft classifier form for arm64
    val displayName: String = "linux-arm64",
) {
    companion object {
        /** The default target for this project. */
        val DEFAULT = TargetPlatform()
    }
}

/** Split a Minecraft library classifier into (os, arch) when it encodes one, e.g. "linux-aarch_64" -> ("linux", "aarch_64"). */
fun parseClassifierOsArch(classifier: String): Pair<String, String?> {
    return when {
        classifier == "natives-linux" -> Pair("linux", null)
        classifier == "natives-windows" -> Pair("windows", null)
        classifier == "natives-macos" -> Pair("osx", null)
        classifier.contains("-") -> {
            val (os, arch) = classifier.split("-", limit = 2)
            Pair(os, arch)
        }
        else -> Pair(classifier, null)
    }
}

/** Whether a native entry applies to the given target platform. */
fun nativeMatchesPlatform(classifier: String, arch: String?, platform: TargetPlatform): Boolean {
    val (os, classifierArch) = parseClassifierOsArch(classifier)
    if (os != platform.osName) return false
    // Arch-neutral natives (e.g. "natives-linux") apply to any arch of that OS.
    if (arch == null && classifierArch == null) return true
    // Classifier-arch must equal the target arch.
    return classifierArch == platform.osArch
}
