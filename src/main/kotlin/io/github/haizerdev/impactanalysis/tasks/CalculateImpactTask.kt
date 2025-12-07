package io.github.haizerdev.impactanalysis.tasks

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.haizerdev.impactanalysis.extension.ImpactRunMode
import io.github.haizerdev.impactanalysis.extension.TestTypeRule
import io.github.haizerdev.impactanalysis.git.GitClient
import io.github.haizerdev.impactanalysis.git.GitDiffEntry
import io.github.haizerdev.impactanalysis.model.ChangedFile
import io.github.haizerdev.impactanalysis.model.FileLanguage
import io.github.haizerdev.impactanalysis.model.ImpactAnalysisResult
import io.github.haizerdev.impactanalysis.model.ImpactReport
import io.github.haizerdev.impactanalysis.model.TestType
import io.github.haizerdev.impactanalysis.report.ReportFormatter
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.Serializable

/**
 * Task for calculating impact analysis
 * Configuration cache compatible - all data is serialized during configuration phase
 */
abstract class CalculateImpactTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val baseBranch: Property<String>

    @get:Input
    @get:Optional
    abstract val compareBranch: Property<String>

    @get:Input
    abstract val includeUncommittedChanges: Property<Boolean>

    @get:Internal
    abstract val rootProjectDir: DirectoryProperty

    @get:Input
    abstract val lintFileExtensions: ListProperty<String>

    @get:Input
    abstract val runAllTestsOnCriticalChanges: Property<Boolean>

    @get:Input
    abstract val runUnitTestsByDefault: Property<Boolean>

    @get:Input
    abstract val criticalPaths: ListProperty<String>

    @get:Input
    abstract val testTypeRulesData: MapProperty<String, SerializableTestTypeRule>

    /**
     * Serialized module dependency graph
     * Map of module -> list of modules it depends on
     */
    @get:Input
    abstract val moduleDependencies: MapProperty<String, List<String>>

    /**
     * Serialized reverse dependencies
     * Map of module -> list of modules that depend on it
     */
    @get:Input
    abstract val moduleReverseDependencies: MapProperty<String, List<String>>

    /**
     * Map of module path to its project directory (relative to root)
     */
    @get:Input
    abstract val moduleDirectories: MapProperty<String, String>

    /**
     * Set of test task suffixes available per module
     * Map of module -> list of test task names
     */
    @get:Input
    abstract val availableTestTasks: MapProperty<String, List<String>>

    /**
     * Android build variant for unit tests (e.g., "Debug")
     */
    @get:Input
    @get:Optional
    abstract val androidUnitTestVariant: Property<String>

    /**
     * Android build variant for instrumented tests (e.g., "Debug")
     */
    @get:Input
    @get:Optional
    abstract val androidInstrumentedTestVariant: Property<String>

    @get:Input
    abstract val mode: Property<ImpactRunMode>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "impact analysis"
        description = "Calculate impact analysis based on Git changes"

        // Save result to build/impact-analysis/result.json by default
        outputFile.convention(
            project.layout.buildDirectory.file("impact-analysis/result.json")
        )

        // Disable up-to-date check, as task depends on Git state
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun execute() {
        val rootDir = rootProjectDir.get().asFile

        val scriptDir = outputFile.get().asFile.parentFile
        val pythonScript = java.io.File(scriptDir, "impact_tests_launcher.py")
        val bashScript = java.io.File(scriptDir, "run_impact_tests.sh")
        val compilerScript = java.io.File(scriptDir, "compile_impact.sh")

        val runMode = mode.get()
        when (runMode) {
            ImpactRunMode.PYTHON -> {
                // Удаляем bash-скрипт если был
                if (bashScript.exists()) bashScript.delete()
                if (compilerScript.exists()) compilerScript.delete()
                copyBuiltinPythonScript()
            }

            ImpactRunMode.BASH -> {
                // Удаляем python скрипт если был
                if (pythonScript.exists()) pythonScript.delete()
                if (compilerScript.exists()) compilerScript.delete()
                prepareBashLauncher()
                prepareBashCompiler()
            }

            else -> {
                // Если выбран gradle — удаляем оба скрипта как неактуальные
                if (pythonScript.exists()) pythonScript.delete()
                if (bashScript.exists()) bashScript.delete()
                if (compilerScript.exists()) compilerScript.delete()
            }
        }

        // Reconstruct extension data from properties
        val extensionData = ExtensionData(
            lintFileExtensions = lintFileExtensions.get(),
            runAllTestsOnCriticalChanges = runAllTestsOnCriticalChanges.get(),
            runUnitTestsByDefault = runUnitTestsByDefault.get(),
            criticalPaths = criticalPaths.get(),
            testTypeRules = testTypeRulesData.get().mapKeys { TestType.valueOf(it.key) }
                .mapValues { it.value.toTestTypeRule() }
        )

        // Create serialized dependency graph
        val dependencyGraph = SerializedDependencyGraph(
            dependencies = moduleDependencies.get(),
            reverseDependencies = moduleReverseDependencies.get(),
            modules = moduleDirectories.get().keys
        )

        // Create serialized dependency analyzer
        val dependencyAnalyzer = SerializedDependencyAnalyzer(
            rootDir = rootDir,
            moduleDirectories = moduleDirectories.get()
        )

        // Create test scope calculator with serialized data
        val testScopeCalculator = SerializedTestScopeCalculator(
            dependencyGraph = dependencyGraph,
            dependencyAnalyzer = dependencyAnalyzer,
            config = extensionData,
            availableTestTasks = availableTestTasks.get(),
            androidUnitTestVariant = androidUnitTestVariant.get(),
            androidInstrumentedTestVariant = androidInstrumentedTestVariant.get()
        )

        // Create components
        val gitClient = GitClient(rootDir)

        try {
            // Get changes from Git
            val gitChanges = mutableListOf<GitDiffEntry>()

            if (includeUncommittedChanges.get()) {
                gitChanges.addAll(gitClient.getUncommittedChanges())
            }

            val base = baseBranch.get()
            val compare = compareBranch.get()

            try {
                gitChanges.addAll(gitClient.getChangedFiles(base, compare))
            } catch (e: Exception) {
                logger.warn("Failed to get changes between $base and $compare: ${e.message}")
            }

            if (gitChanges.isEmpty()) {
                logger.lifecycle("No changes detected")
                writeEmptyResult()
                return
            }

            // Convert to ChangedFile
            val changedFiles = gitChanges.map { gitEntry ->
                val path = gitEntry.newPath
                val module = dependencyAnalyzer.getModuleForFile(path)

                ChangedFile(
                    path = path,
                    module = module,
                    changeType = gitEntry.changeType,
                    language = FileLanguage.fromPath(path)
                )
            }

            logger.lifecycle("Found ${changedFiles.size} changed files")

            // Determine affected modules
            val directModules = changedFiles.mapNotNull { it.module }.toSet()
            val affectedModules = dependencyGraph.getAffectedModules(directModules).filterRootModule()

            logger.lifecycle("Directly changed modules: $directModules")
            logger.lifecycle("All affected modules: $affectedModules")

            // Calculate test scope
            val testsToRun = testScopeCalculator.calculateTestScope(changedFiles)

            testsToRun.forEach { (type, tasks) ->
                logger.lifecycle("$type tests: ${tasks.size} tasks")
                tasks.forEach { task ->
                    logger.lifecycle("  - $task")
                }
            }

            // Generate impact report with statistics
            val report = generateImpactReport(
                testsToRun = testsToRun,
                availableTestTasks = availableTestTasks.get()
            )

            // Log report summary using ReportFormatter
            ReportFormatter.formatSummary(report).forEach { line ->
                logger.lifecycle(line)
            }

            // Determine files to lint
            val lintExts = extensionData.lintFileExtensions
            val filesToLint = changedFiles
                .filter { file ->
                    val fileExtension = file.path.substringAfterLast('.', "")
                    fileExtension in lintExts
                }
                .map { it.path }

            logger.lifecycle("Files to lint: ${filesToLint.size}")

            // Create result
            val result = ImpactAnalysisResult(
                changedFiles = changedFiles,
                affectedModules = affectedModules,
                testsToRun = testsToRun,
                filesToLint = filesToLint,
                report = report
            )

            // Save result to JSON
            saveResult(result)

            logger.lifecycle("Impact analysis result saved to: ${outputFile.get().asFile}")

        } finally {
            gitClient.close()
        }
    }

    /**
     * Копирует встроенный Python-скрипт для запуска тестов в build/impact-analysis/impact_tests_launcher.py
     */
    private fun copyBuiltinPythonScript() {
        // build/impact-analysis/
        val scriptDir = outputFile.get().asFile.parentFile
        scriptDir.mkdirs()
        val scriptFile = java.io.File(scriptDir, "impact_tests_launcher.py")
        val stream = javaClass.getResourceAsStream("/impact_tests_launcher.py")
        if (stream == null) {
            logger.warn("Builtin Python script resource not found! You must add impact_tests_launcher.py to resources!")
            return
        }
        scriptFile.outputStream().use { out ->
            stream.copyTo(out)
        }
        logger.lifecycle("Copied builtin Python test launcher to: ${scriptFile.absolutePath}")
    }

    /**
     * Generates a bash script to run impact tests (run_impact_tests.sh)
     */
    private fun prepareBashLauncher() {
        val resultFile = outputFile.get().asFile
        if (!resultFile.exists()) return
        val resultJson = resultFile.readText()
        val tasks = mutableListOf<String>()
        val report: ImpactReport?

        try {
            val gson = Gson()
            val result = gson.fromJson(resultJson, ImpactAnalysisResult::class.java)
            report = result.report

            result.testsToRun.values.forEach { testList ->
                tasks.addAll(testList)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to parse result.json for bash launcher: $t")
            return
        }

        if (tasks.isEmpty()) {
            logger.lifecycle("No tests to run, so no bash launcher script generated.")
            return
        }

        val bashScript = outputFile.get().asFile.parentFile.resolve("run_impact_tests.sh")
        val newLineSeparator = " \\"

        val template = StringBuilder()
            .appendLine("#!/bin/bash")
            .appendLine("# This script was generated by the Impact Analysis Plugin.")
            .appendLine("# Use this script to run the impacted tests: ./build/impact-analysis/run_impact_tests.sh")
            .appendLine("")
            .appendLine("# Print impact analysis report")
            .apply {
                if (report != null) {
                    append(ReportFormatter.formatForBashScript(report))
                }
            }
            .appendLine("")
            .appendLine("./gradlew$newLineSeparator")
            .apply {
                tasks.forEach { task ->
                    appendLine("$task$newLineSeparator")
                }
            }
            .appendLine("--continue --parallel")
            .toString()

        bashScript.writeText(template)
        bashScript.setExecutable(true)
        logger.lifecycle("Generated bash script to run impacted tests: ${bashScript.absolutePath}")
    }

    /**
     * Generates a bash script to compile affected modules (compile_impact.sh)
     */
    private fun prepareBashCompiler() {
        val resultFile = outputFile.get().asFile
        if (!resultFile.exists()) return
        val resultJson = resultFile.readText()
        val affectedModules: Set<String>
        val report: ImpactReport?

        try {
            val gson = Gson()
            val result = gson.fromJson(resultJson, ImpactAnalysisResult::class.java)
            affectedModules = result.affectedModules
            report = result.report
        } catch (t: Throwable) {
            logger.warn("Failed to parse result.json for bash compiler: $t")
            return
        }

        if (affectedModules.isEmpty()) {
            logger.lifecycle("No affected modules to compile, so no bash compiler script generated.")
            return
        }

        // Generate compilation tasks for affected modules
        // For Android modules: assemble tasks (assembleDebug, assembleRelease)
        // For JVM modules: build or classes tasks
        val compileTasks = mutableListOf<String>()

        affectedModules.forEach { modulePath ->
            // Try to determine if it's an Android module or JVM module
            val moduleDir = SerializedDependencyAnalyzer(
                rootDir = rootProjectDir.get().asFile,
                moduleDirectories = moduleDirectories.get()
            ).getAbsoluteModuleDir(modulePath)

            if (moduleDir != null && moduleDir.exists()) {
                val hasBuildGradle = moduleDir.resolve("build.gradle").exists() ||
                        moduleDir.resolve("build.gradle.kts").exists()

                if (hasBuildGradle) {
                    // Check for Android plugin
                    val buildFile = if (moduleDir.resolve("build.gradle.kts").exists()) {
                        moduleDir.resolve("build.gradle.kts")
                    } else {
                        moduleDir.resolve("build.gradle")
                    }

                    val buildContent = buildFile.readText()
                    val isAndroidModule = buildContent.contains("com.android.application") ||
                            buildContent.contains("com.android.library") ||
                            buildContent.contains("android {")

                    // Generate appropriate compile task
                    val taskName = when {
                        isAndroidModule && androidUnitTestVariant.get().isNotEmpty() -> {
                            // For Android modules, use assemble with variant
                            val variant = androidUnitTestVariant.get()
                            "assemble${variant.replaceFirstChar { it.uppercase() }}"
                        }

                        isAndroidModule -> "assembleDebug" // Default to Debug
                        else -> "classes" // For JVM modules, compile classes
                    }

                    compileTasks.add("$modulePath:$taskName")
                }
            }
        }

        if (compileTasks.isEmpty()) {
            logger.lifecycle("No compilation tasks generated for affected modules.")
            return
        }

        val bashScript = outputFile.get().asFile.parentFile.resolve("compile_impact.sh")
        val newLineSeparator = " \\"

        val template = StringBuilder()
            .appendLine("#!/bin/bash")
            .appendLine("# This script was generated by the Impact Analysis Plugin.")
            .appendLine("# Use this script to compile the affected modules: ./build/impact-analysis/compile_impact.sh")
            .appendLine("")
            .appendLine("# Impact Analysis - Compilation Report")
            .apply {
                if (report != null) {
                    appendLine("echo '========================================='")
                    appendLine("echo 'Impact Analysis - Compilation Report'")
                    appendLine("echo '========================================='")
                    appendLine("echo 'Affected modules to compile: ${affectedModules.size}'")
                    appendLine("echo 'Compilation tasks: ${compileTasks.size}'")
                    appendLine("echo '========================================='")
                    appendLine("echo ''")
                }
            }
            .appendLine("./gradlew$newLineSeparator")
            .apply {
                compileTasks.forEach { task ->
                    appendLine("$task$newLineSeparator")
                }
            }
            .appendLine("--parallel")
            .toString()

        bashScript.writeText(template)
        bashScript.setExecutable(true)
        logger.lifecycle("Generated bash script to compile affected modules: ${bashScript.absolutePath}")
    }

    private fun saveResult(result: ImpactAnalysisResult) {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()

        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        file.writeText(gson.toJson(result))
    }

    private fun writeEmptyResult() {
        val emptyResult = ImpactAnalysisResult(
            changedFiles = emptyList(),
            affectedModules = emptySet(),
            testsToRun = emptyMap(),
            filesToLint = emptyList()
        )
        saveResult(emptyResult)
    }

    private fun generateImpactReport(
        testsToRun: Map<TestType, List<String>>,
        availableTestTasks: Map<String, List<String>>
    ): ImpactReport {
        val totalTestsToRun = testsToRun.values.sumOf { it.size }

        // Get only real modules that have tests (not container directories)
        val realModulesWithTests = availableTestTasks
            .filterValues { it.isNotEmpty() }
            .keys
            .filterRootModule()

        // Calculate total tests that WOULD be run if all modules were tested
        // (based on the same variant we're using, not all available variants)
        val totalPotentialTests = realModulesWithTests.count { modulePath ->
            val tasks = availableTestTasks[modulePath] ?: emptyList()
            val moduleDir = SerializedDependencyAnalyzer(
                rootDir = rootProjectDir.get().asFile,
                moduleDirectories = moduleDirectories.get()
            ).getAbsoluteModuleDir(modulePath)

            moduleDir != null && tasks.isNotEmpty()
        }

        val totalTestsSkipped = totalPotentialTests - totalTestsToRun

        // Extract module names from task paths (e.g., ":app:testDebugUnitTest" -> ":app")
        val modulesToRun = testsToRun.values.flatten().mapNotNull { task ->
            val lastColonIndex = task.lastIndexOf(':')
            if (lastColonIndex > 0) task.substring(0, lastColonIndex) else null
        }.toSet()

        // Only include real modules with tests in skipped modules
        val skippedModules = realModulesWithTests - modulesToRun

        // Count actual test methods in modules
        val testMethodsByModule = mutableMapOf<String, Int>()
        var totalTestMethodsToRun = 0
        var totalTestMethodsSkipped = 0

        realModulesWithTests.forEach { modulePath ->
            val moduleDir = SerializedDependencyAnalyzer(
                rootDir = rootProjectDir.get().asFile,
                moduleDirectories = moduleDirectories.get()
            ).getAbsoluteModuleDir(modulePath)

            if (moduleDir != null) {
                val testMethodCount = countTestMethodsInModule(moduleDir)
                testMethodsByModule[modulePath] = testMethodCount

                if (modulePath in modulesToRun) {
                    totalTestMethodsToRun += testMethodCount
                } else {
                    totalTestMethodsSkipped += testMethodCount
                }
            }
        }

        // Estimate time saved based on test methods (more accurate than test tasks)
        // Algorithm:
        // - Average test execution: ~0.5 seconds per test method
        // - Gradle task overhead: ~5 seconds per test task (compilation, initialization)
        // Formula: (test_methods * 0.5s) + (test_tasks_with_tests * 5s)

        // Calculate time in minutes:
        // - Test methods: 0.5 sec = 0.5/60 = 0.00833 minutes per test
        // - Task overhead: 5 sec = 5/60 = 0.0833 minutes per task (only for tasks that have tests)

        // Only count tasks that actually have test methods
        val skippedTasksWithTests = skippedModules.count { modulePath ->
            (testMethodsByModule[modulePath] ?: 0) > 0
        }

        val testMethodTime = totalTestMethodsSkipped * 0.00833 // 0.5 seconds per test method
        val taskOverheadTime = skippedTasksWithTests * 0.0833 // 5 seconds Gradle overhead per task with tests
        val estimatedTimeSavedMinutes = testMethodTime + taskOverheadTime

        val testsByType = testsToRun.mapValues { it.value.size }

        return ImpactReport(
            totalTestsToRun = totalTestsToRun,
            totalTestsSkipped = totalTestsSkipped,
            modulesToRun = modulesToRun,
            skippedModules = skippedModules,
            estimatedTimeSavedMinutes = estimatedTimeSavedMinutes,
            testsByType = testsByType,
            totalTestMethodsToRun = totalTestMethodsToRun,
            totalTestMethodsSkipped = totalTestMethodsSkipped,
            testMethodsByModule = testMethodsByModule
        )
    }

    /**
     * Count test methods in a module by scanning test files
     * Looks for @Test annotations and test function declarations
     */
    private fun countTestMethodsInModule(moduleDir: java.io.File): Int {
        if (!moduleDir.exists() || !moduleDir.isDirectory) return 0

        var testCount = 0
        val testDirs = listOf(
            "src/test/kotlin",
            "src/test/java",
            "src/androidTest/kotlin",
            "src/androidTest/java",
            "src/androidTestDebug/kotlin",
            "src/androidTestDebug/java"
        )

        testDirs.forEach { testDirPath ->
            val testDir = moduleDir.resolve(testDirPath)
            if (testDir.exists()) {
                testDir.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .forEach { file ->
                        testCount += countTestMethodsInFile(file)
                    }
            }
        }

        return testCount
    }

    /**
     * Count test methods in a single file
     * Looks for @Test annotations and Kotlin test functions
     */
    private fun countTestMethodsInFile(file: java.io.File): Int {
        try {
            val content = file.readText()
            var count = 0

            // Count @Test annotations (both JUnit 4 and JUnit 5)
            count += Regex("""@Test\s*(\(|\n|$)""").findAll(content).count()

            // Count @RepeatedTest annotations (JUnit 5)
            count += Regex("""@RepeatedTest\s*\(""").findAll(content).count()

            // Count @ParameterizedTest annotations (JUnit 5)
            count += Regex("""@ParameterizedTest\s*(\n|$)""").findAll(content).count()

            // For Kotlin: count "fun `test" patterns (common in Kotlin tests)
            if (file.extension == "kt") {
                // Match patterns like: fun `test something`()
                count += Regex("""fun\s+`test\s+[^`]+`\s*\(""").findAll(content).count()
            }

            return count
        } catch (e: Exception) {
            logger.debug("Failed to count tests in ${file.path}: ${e.message}")
            return 0
        }
    }

}

/**
 * Serializable version of TestTypeRule for task inputs
 */
data class SerializableTestTypeRule(
    val pathPatterns: List<String> = emptyList(),
    val runOnlyInChangedModules: Boolean = false,
    val isEnable: Boolean = false,
) : Serializable {
    fun toTestTypeRule(): TestTypeRule {
        return TestTypeRule().apply {
            pathPatterns.addAll(this@SerializableTestTypeRule.pathPatterns)
            runOnlyInChangedModules = this@SerializableTestTypeRule.runOnlyInChangedModules
            isEnable = this@SerializableTestTypeRule.isEnable
        }
    }

    companion object {
        fun fromRule(rule: TestTypeRule) = SerializableTestTypeRule(
            pathPatterns = rule.pathPatterns,
            runOnlyInChangedModules = rule.runOnlyInChangedModules,
            isEnable = rule.isEnable
        )
    }
}

/**
 * Extension data holder
 */
data class ExtensionData(
    override val lintFileExtensions: List<String>,
    override val runAllTestsOnCriticalChanges: Boolean,
    override val runUnitTestsByDefault: Boolean,
    override val criticalPaths: List<String>,
    override val testTypeRules: Map<TestType, TestTypeRule>
) : io.github.haizerdev.impactanalysis.scope.ImpactAnalysisConfig

/**
 * Serialized dependency graph - configuration cache compatible
 */
class SerializedDependencyGraph(
    private val dependencies: Map<String, List<String>>,
    private val reverseDependencies: Map<String, List<String>>,
    private val modules: Set<String>
) {
    /**
     * Get all modules that depend on given module (directly or transitively)
     */
    fun getAffectedModules(changedModules: Set<String>): Set<String> {
        val affected = mutableSetOf<String>()
        val toProcess = changedModules.toMutableSet()

        while (toProcess.isNotEmpty()) {
            val current = toProcess.first()
            toProcess.remove(current)

            if (affected.add(current)) {
                // Add all modules that depend on current one
                reverseDependencies[current]?.forEach { dependent ->
                    if (dependent !in affected) {
                        toProcess.add(dependent)
                    }
                }
            }
        }

        return affected
    }

    fun getDirectDependencies(modulePath: String): Set<String> {
        return dependencies[modulePath]?.toSet() ?: emptySet()
    }

    fun getDirectDependents(modulePath: String): Set<String> {
        return reverseDependencies[modulePath]?.toSet() ?: emptySet()
    }

    fun getAllModules(): Set<String> = modules
}

/**
 * Serialized dependency analyzer - configuration cache compatible
 */
class SerializedDependencyAnalyzer(
    private val rootDir: java.io.File,
    private val moduleDirectories: Map<String, String>
) {
    private val modulePathCache = mutableMapOf<String, String?>()

    /**
     * Determine which module a file belongs to
     */
    fun getModuleForFile(filePath: String): String? {
        if (modulePathCache.containsKey(filePath)) {
            return modulePathCache[filePath]
        }

        val module = inferModuleFromPath(filePath)
        modulePathCache[filePath] = module
        return module
    }

    /**
     * Try to infer module from file path
     */
    private fun inferModuleFromPath(filePath: String): String? {
        val normalizedPath = filePath.replace("\\", "/")

        // Find the module with the longest matching directory
        var bestMatch: String? = null
        var longestMatch = 0

        moduleDirectories.forEach { (modulePath, moduleDir) ->
            val normalizedModuleDir = moduleDir.replace("\\", "/")
            if (normalizedPath.startsWith(normalizedModuleDir + "/") || normalizedPath.startsWith(normalizedModuleDir + "\\")) {
                val matchLength = normalizedModuleDir.length
                if (matchLength > longestMatch) {
                    longestMatch = matchLength
                    bestMatch = modulePath
                }
            }
        }

        return bestMatch
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
        val fileName = java.io.File(filePath).name
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

    /**
     * Check if file is a root-level (project-wide) configuration file
     * Root-level config changes can affect the entire project
     */
    fun isRootLevelConfigFile(filePath: String): Boolean {
        val normalizedPath = filePath.replace("\\", "/")
        val fileName = java.io.File(filePath).name

        // Check if file is in root directory or gradle/ directory
        val pathSegments = normalizedPath.split("/")

        return when {
            // Files directly in root (no directory prefix or only one segment)
            pathSegments.size <= 1 && isConfigFile(filePath) -> true

            // gradle/wrapper/ or gradle/ directory files
            normalizedPath.startsWith("gradle/") -> true

            // settings.gradle(.kts) is always root-level
            fileName == "settings.gradle" || fileName == "settings.gradle.kts" -> true

            // Root gradle.properties
            fileName == "gradle.properties" && pathSegments.size <= 1 -> true

            // Root build.gradle(.kts)
            (fileName == "build.gradle" || fileName == "build.gradle.kts") && pathSegments.size <= 1 -> true

            else -> false
        }
    }

    /**
     * Абсолютный путь к директории модуля
     */
    fun getAbsoluteModuleDir(modulePath: String): java.io.File? {
        val rel = moduleDirectories[modulePath]
        return if (rel == null || rel.isBlank()) rootDir else java.io.File(rootDir, rel)
    }
}

/**
 * Serialized test scope calculator - configuration cache compatible
 */
class SerializedTestScopeCalculator(
    private val dependencyGraph: SerializedDependencyGraph,
    private val dependencyAnalyzer: SerializedDependencyAnalyzer,
    private val config: io.github.haizerdev.impactanalysis.scope.ImpactAnalysisConfig,
    private val availableTestTasks: Map<String, List<String>>,
    private val androidUnitTestVariant: String,
    private val androidInstrumentedTestVariant: String
) {
    /**
     * Calculate which tests need to be run
     */
    fun calculateTestScope(changedFiles: List<ChangedFile>): Map<TestType, List<String>> {
        val result = mutableMapOf<TestType, List<String>>()

        // Determine affected modules
        val directlyAffectedModules = changedFiles.mapNotNull { it.module }.toSet()
        val allAffectedModules = dependencyGraph
            .getAffectedModules(directlyAffectedModules)
            .filterRootModule()

        // Check for root-level critical files ONLY (not module-level)
        val rootLevelCriticalFiles = changedFiles.filter { file ->
            file.module == null && dependencyAnalyzer.isRootLevelConfigFile(file.path)
        }

        val criticalPaths = config.criticalPaths
        val criticalPathFiles = changedFiles.filter { file ->
            file.module == null && criticalPaths.any { file.path.contains(it) }
        }

        val hasCriticalChanges = rootLevelCriticalFiles.isNotEmpty() || criticalPathFiles.isNotEmpty()

        if (rootLevelCriticalFiles.isNotEmpty()) {
            println("Root-level critical files detected:")
            rootLevelCriticalFiles.forEach { println("  - ${it.path}") }
        }

        if (criticalPathFiles.isNotEmpty()) {
            println("Critical path files detected:")
            criticalPathFiles.forEach { println("  - ${it.path}") }
        }

        if (hasCriticalChanges && config.runAllTestsOnCriticalChanges) {
            println("Running all tests in ALL modules due to critical changes")

            // Run all tests in ALL modules of the project (not just affected ones)
            val allProjectModules = dependencyGraph.getAllModules().filterRootModule()
            return getAllTestsForModules(allProjectModules).filterDisabledTestTypes()
        }

        // Determine test types for each affected module
        config.testTypeRules.forEach { (testType, rule) ->
            if (!rule.isEnable) return@forEach // if prohibited, skip
            val modulesToTest = when {
                // If changed files match the rule
                changedFiles.any { file -> rule.shouldRunForFile(file.path) } -> {
                    if (rule.runOnlyInChangedModules) {
                        directlyAffectedModules
                    } else {
                        allAffectedModules
                    }
                }

                else -> emptySet()
            }

            if (modulesToTest.isNotEmpty()) {
                result[testType] = generateTestTasks(modulesToTest, testType)
            }
        }

        // If no specific rules, run unit tests
        if (result.isEmpty() && config.runUnitTestsByDefault) {
            result[TestType.UNIT] = generateTestTasks(allAffectedModules, TestType.UNIT)
        }

        return result.filterDisabledTestTypes()
    }

    private fun Map<TestType, List<String>>.filterDisabledTestTypes(): Map<TestType, List<String>> =
        filterKeys { config.testTypeRules[it]?.isEnable == true }


    /**
     * Get all tests for modules
     */
    private fun getAllTestsForModules(modules: Set<String>): Map<TestType, List<String>> {
        val result = mutableMapOf<TestType, List<String>>()

        println("Getting all tests for modules: ${modules.joinToString(", ")}")

        TestType.entries.filter { it != TestType.ALL }.forEach { testType ->
            result[testType] = generateTestTasks(modules, testType)
        }

        return result
    }

    /**
     * Generate task names for running tests, фильтруя задачи для модулей без src/test или src/androidTest
     */
    private fun generateTestTasks(modules: Set<String>, testType: TestType): List<String> {
        return modules.mapNotNull { modulePath ->
            val tasks = availableTestTasks[modulePath] ?: emptyList()
            val moduleDir = dependencyAnalyzer.getAbsoluteModuleDir(modulePath)

            val hasTests = moduleDir != null && hasTestsInModule(moduleDir, testType)
            if (!hasTests) return@mapNotNull null

            // Generate task name based on test type and Android variant
            val taskName = when {
                // Android unit tests with variant
                testType == TestType.UNIT && androidUnitTestVariant.isNotEmpty() -> {
                    val variantTaskName = "test${androidUnitTestVariant.replaceFirstChar { it.uppercase() }}UnitTest"
                    if (tasks.contains(variantTaskName)) {
                        variantTaskName
                    } else {
                        // Try to find any task that contains the variant name (e.g., testProdReleaseUnitTest)
                        tasks.find {
                            it.startsWith("test") &&
                                    it.endsWith("UnitTest") &&
                                    it.contains(androidUnitTestVariant, ignoreCase = true)
                        } ?: testType.taskSuffix
                    }
                }

                (testType == TestType.UI || testType == TestType.E2E) && androidInstrumentedTestVariant.isNotEmpty() -> {
                    val variantTaskName =
                        "connected${androidInstrumentedTestVariant.replaceFirstChar { it.uppercase() }}AndroidTest"
                    if (tasks.contains(variantTaskName)) {
                        variantTaskName
                    } else {
                        tasks.find {
                            it.startsWith("connected") &&
                                    it.endsWith("AndroidTest") &&
                                    it.contains(androidInstrumentedTestVariant, ignoreCase = true)
                        } ?: testType.taskSuffix
                    }
                }

                else -> testType.taskSuffix
            }

            if (tasks.contains(taskName) || tasks.contains(testType.taskSuffix)) {
                if (modulePath == ":") {
                    taskName
                } else {
                    "$modulePath:$taskName"
                }
            } else {
                null
            }
        }.sorted()
    }

    /**
     * Поиск есть ли хотя бы один тестовый файл в стандартных source setах для указанного типа теста
     */
    private fun hasTestsInModule(moduleDir: java.io.File, testType: TestType): Boolean {
        if (!moduleDir.exists() || !moduleDir.isDirectory) return false
        val testDirs = mutableListOf("src/test")
        if (testType == TestType.UI || testType == TestType.E2E) {
            testDirs += listOf("src/androidTest", "src/androidTestDebug", "src/androidTestRelease")
        }
        return testDirs.any { relPath ->
            val absDir = moduleDir.resolve(relPath)
            absDir.exists() && absDir.walkTopDown().any {
                it.isFile && (it.extension == "kt" || it.extension == "java")
            }
        }
    }
}

private fun Set<String>.filterRootModule(): Set<String> = filterNot { it == ":" }.toSet()

/**
 * Task for printing impact analysis report
 * Reads the result.json and displays formatted statistics
 */
abstract class PrintImpactReportTask : DefaultTask() {

    @get:InputFile
    abstract val resultFile: RegularFileProperty

    init {
        group = "impact analysis"
        description = "Print impact analysis report from the last analysis"

        // Set default result file location
        resultFile.convention(
            project.layout.buildDirectory.file("impact-analysis/result.json")
        )
    }

    @TaskAction
    fun execute() {
        val file = resultFile.get().asFile

        if (!file.exists()) {
            logger.error("Impact analysis result not found at: ${file.absolutePath}")
            logger.error("Please run 'calculateImpactAnalysis' task first")
            return
        }

        try {
            val gson = Gson()
            val result = gson.fromJson(file.readText(), ImpactAnalysisResult::class.java)
            val report = result.report

            if (report == null) {
                logger.lifecycle("No report data available in result file")
                return
            }

            // Print beautiful report using ReportFormatter
            println()
            println(ReportFormatter.formatAsBox(report, includeDetails = true))
            println()
            println("Changed files: ${result.changedFiles.size}")
            println("Affected modules: ${result.affectedModules.size}")
            println("Files to lint: ${result.filesToLint.size}")
            println()

        } catch (e: Exception) {
            logger.error("Failed to parse result file: ${e.message}", e)
        }
    }
}
