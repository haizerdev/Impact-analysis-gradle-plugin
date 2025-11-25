package com.nzr.impactanalysis.dependency

import com.nzr.impactanalysis.model.ChangedFile
import org.gradle.api.Project
import java.io.File

/**
 * Dependency analyzer for determining affected modules
 */
class DependencyAnalyzer(private val rootProject: Project) {

    private val modulePathCache = mutableMapOf<String, String?>()

    /**
     * Determine which module a file belongs to
     */
    fun getModuleForFile(filePath: String): String? {
        if (modulePathCache.containsKey(filePath)) {
            return modulePathCache[filePath]
        }

        val file = File(rootProject.projectDir, filePath)
        if (!file.exists() && !file.absolutePath.contains("/build/")) {
            // File is deleted or doesn't exist
            // Try to infer module from path
            val modulePath = inferModuleFromPath(filePath)
            modulePathCache[filePath] = modulePath
            return modulePath
        }

        val module = findModuleForFile(file)
        modulePathCache[filePath] = module
        return module
    }

    /**
     * Get modules affected by changes
     */
    fun getAffectedModules(changedFiles: List<ChangedFile>): Set<String> {
        return changedFiles.mapNotNull { it.module }.toSet()
    }

    /**
     * Find module for file by checking directory hierarchy
     */
    private fun findModuleForFile(file: File): String? {
        var currentDir = file.parentFile

        while (currentDir != null && currentDir.path.startsWith(rootProject.projectDir.path)) {
            // Look for build.gradle or build.gradle.kts
            val hasBuildFile = currentDir.listFiles()?.any {
                it.name == "build.gradle" || it.name == "build.gradle.kts"
            } ?: false

            if (hasBuildFile) {
                // Found module directory
                val relativePath = currentDir.relativeTo(rootProject.projectDir).path
                return pathToModuleName(relativePath)
            }

            currentDir = currentDir.parentFile
        }

        return null // File in project root
    }

    /**
     * Try to infer module from file path
     */
    private fun inferModuleFromPath(filePath: String): String? {
        val parts = filePath.split(File.separator, "/")

        // Check if module with this name exists
        if (parts.isNotEmpty()) {
            val potentialModule = ":${parts[0]}"
            val project = rootProject.findProject(potentialModule)
            if (project != null) {
                // If there are more nesting levels
                for (i in 1 until parts.size) {
                    val nestedModule = ":" + parts.take(i + 1).joinToString(":")
                    val nestedProject = rootProject.findProject(nestedModule)
                    if (nestedProject != null) {
                        return nestedModule
                    } else {
                        return ":" + parts.take(i).joinToString(":")
                    }
                }
                return potentialModule
            }
        }

        return null
    }

    /**
     * Convert path to Gradle module name
     */
    private fun pathToModuleName(relativePath: String): String {
        if (relativePath.isEmpty() || relativePath == ".") {
            return ":"
        }
        return ":" + relativePath.replace(File.separator, ":").replace("/", ":")
    }

    /**
     * Check if file is a test file
     */
    fun isTestFile(filePath: String): Boolean {
        val normalizedPath = filePath.replace("\\", "/")
        return normalizedPath.contains("/test/") ||
                normalizedPath.contains("/androidTest/") ||
                normalizedPath.contains("/androidTestDebug/") ||
                normalizedPath.contains("/androidTestRelease/") ||
                normalizedPath.endsWith("Test.kt") ||
                normalizedPath.endsWith("Test.java") ||
                normalizedPath.endsWith("Tests.kt") ||
                normalizedPath.endsWith("Tests.java") ||
                normalizedPath.endsWith("Spec.kt")
    }

    /**
     * Check if file is a configuration file
     */
    fun isConfigFile(filePath: String): Boolean {
        val fileName = File(filePath).name
        val normalizedPath = filePath.replace("\\", "/")

        return fileName == "build.gradle" ||
                fileName == "build.gradle.kts" ||
                fileName == "settings.gradle" ||
                fileName == "settings.gradle.kts" ||
                fileName == "gradle.properties" ||
                fileName.endsWith(".properties") ||
                fileName.endsWith(".pro") || // ProGuard
                normalizedPath.contains("gradle/")
    }
}
