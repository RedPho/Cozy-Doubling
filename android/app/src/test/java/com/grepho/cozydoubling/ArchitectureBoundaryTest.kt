package com.grepho.cozydoubling

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureBoundaryTest {

    private fun resolveSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/grepho/cozydoubling"),
            File("app/src/main/java/com/grepho/cozydoubling")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate Android source root from working dir: ${File(".").absolutePath}")
    }

    @Test
    fun clientLayersOutsideDataAndDiMustNotCoupleToBackendOrNetwork() {
        val sourceRoot = resolveSourceRoot()
        val ktFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("Expected Kotlin source files under $sourceRoot", ktFiles.isNotEmpty())

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val normalizedPath = file.relativeTo(sourceRoot).path.replace('\\', '/')
            val isDataLayer = normalizedPath.startsWith("data/")
            val isDiCompositionRoot = normalizedPath.startsWith("core/di/")

            if (!isDataLayer && !isDiCompositionRoot) {
                val content = file.readText()
                if (content.contains("com.grepho.cozydoubling.data.")) {
                    violations.add("$normalizedPath imports data implementation package")
                }
                if (content.contains("okhttp3.")) {
                    violations.add("$normalizedPath depends directly on okhttp3")
                }
                if (content.contains("@SerialName(")) {
                    violations.add("$normalizedPath contains backend wire annotation @SerialName")
                }
            }

            if (normalizedPath.startsWith("core/") || normalizedPath.startsWith("features/")) {
                if (file.name.endsWith("Repository.kt")) {
                    val content = file.readText()
                    if (Regex("""fun\s+\w*Backend\w*""").containsMatchIn(content)) {
                        violations.add("$normalizedPath exposes backend-specific method name in repository interface")
                    }
                }
            }
        }

        assertTrue(
            "Clean architecture boundary violations:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
