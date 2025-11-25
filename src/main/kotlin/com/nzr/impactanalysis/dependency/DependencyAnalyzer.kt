package com.nzr.impactanalysis.dependency

import com.nzr.impactanalysis.model.ChangedFile
import org.gradle.api.Project
import java.io.File

/**
 * Анализатор зависимостей для определения затронутых модулей
 */
class DependencyAnalyzer(private val rootProject: Project) {

    private val modulePathCache = mutableMapOf<String, String?>()

    /**
     * Определить модуль, к которому относится файл
     */
    fun getModuleForFile(filePath: String): String? {
        if (modulePathCache.containsKey(filePath)) {
            return modulePathCache[filePath]
        }

        val file = File(rootProject.projectDir, filePath)
        if (!file.exists() && !file.absolutePath.contains("/build/")) {
            // Файл удален или не существует
            // Пытаемся определить модуль по пути
            val modulePath = inferModuleFromPath(filePath)
            modulePathCache[filePath] = modulePath
            return modulePath
        }

        val module = findModuleForFile(file)
        modulePathCache[filePath] = module
        return module
    }

    /**
     * Получить модули, затронутые изменениями
     */
    fun getAffectedModules(changedFiles: List<ChangedFile>): Set<String> {
        return changedFiles.mapNotNull { it.module }.toSet()
    }

    /**
     * Найти модуль для файла, проверяя иерархию директорий
     */
    private fun findModuleForFile(file: File): String? {
        var currentDir = file.parentFile

        while (currentDir != null && currentDir.path.startsWith(rootProject.projectDir.path)) {
            // Ищем build.gradle или build.gradle.kts
            val hasBuildFile = currentDir.listFiles()?.any {
                it.name == "build.gradle" || it.name == "build.gradle.kts"
            } ?: false

            if (hasBuildFile) {
                // Нашли директорию модуля
                val relativePath = currentDir.relativeTo(rootProject.projectDir).path
                return pathToModuleName(relativePath)
            }

            currentDir = currentDir.parentFile
        }

        return null // Файл в корне проекта
    }

    /**
     * Попытаться определить модуль по пути файла
     */
    private fun inferModuleFromPath(filePath: String): String? {
        val parts = filePath.split(File.separator, "/")

        // Проверяем, есть ли модуль с таким именем
        if (parts.isNotEmpty()) {
            val potentialModule = ":${parts[0]}"
            val project = rootProject.findProject(potentialModule)
            if (project != null) {
                // Если есть больше уровней вложенности
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
     * Преобразовать путь в имя модуля Gradle
     */
    private fun pathToModuleName(relativePath: String): String {
        if (relativePath.isEmpty() || relativePath == ".") {
            return ":"
        }
        return ":" + relativePath.replace(File.separator, ":").replace("/", ":")
    }

    /**
     * Проверить, является ли файл тестовым
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
     * Проверить, является ли файл конфигурационным
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
