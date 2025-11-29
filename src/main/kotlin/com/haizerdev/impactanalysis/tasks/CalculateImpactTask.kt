package com.haizerdev.impactanalysis.tasks

import com.google.gson.GsonBuilder
import com.haizerdev.impactanalysis.dependency.DependencyAnalyzer
import com.haizerdev.impactanalysis.dependency.ModuleDependencyGraph
import com.haizerdev.impactanalysis.extension.ImpactAnalysisExtension
import com.haizerdev.impactanalysis.extension.TestTypeRule
import com.haizerdev.impactanalysis.git.GitClient
import com.haizerdev.impactanalysis.model.ChangedFile
import com.haizerdev.impactanalysis.model.FileLanguage
import com.haizerdev.impactanalysis.model.ImpactAnalysisResult
import com.haizerdev.impactanalysis.model.TestType
import com.haizerdev.impactanalysis.scope.TestScopeCalculator
import com.haizerdev.impactanalysis.git.GitDiffEntry
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.Serializable
import javax.inject.Inject

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
     * List of all modules in the project
     */
    @get:Input
    abstract val allModules: ListProperty<String>

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
            modules = allModules.get().toSet()
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
            val affectedModules = dependencyGraph.getAffectedModules(directModules).filterNot { it == ":" }.toSet()

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
                filesToLint = filesToLint
            )

            // Save result to JSON
            saveResult(result)

            logger.lifecycle("Impact analysis result saved to: ${outputFile.get().asFile}")

        } finally {
            gitClient.close()
        }
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
) : com.haizerdev.impactanalysis.scope.ImpactAnalysisConfig

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
}

/**
 * Serialized test scope calculator - configuration cache compatible
 */
class SerializedTestScopeCalculator(
    private val dependencyGraph: SerializedDependencyGraph,
    private val dependencyAnalyzer: SerializedDependencyAnalyzer,
    private val config: com.haizerdev.impactanalysis.scope.ImpactAnalysisConfig,
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
        val allAffectedModules = dependencyGraph.getAffectedModules(directlyAffectedModules).filterNot { it == ":" }.toSet()

        // Check critical files
        val criticalPaths = config.criticalPaths
        val hasCriticalChanges = changedFiles.any { file ->
            dependencyAnalyzer.isConfigFile(file.path) ||
                    criticalPaths.any { file.path.contains(it) }
        }

        if (hasCriticalChanges && config.runAllTestsOnCriticalChanges) {
            // Run all tests in all modules
            return getAllTestsForModules(allAffectedModules).filterDisabledTestTypes()
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

    private fun Map<TestType, List<String>>.filterDisabledTestTypes(): Map<TestType, List<String>> = filterKeys { config.testTypeRules[it]?.isEnable == true }

    /**
     * Get all tests for modules
     */
    private fun getAllTestsForModules(modules: Set<String>): Map<TestType, List<String>> {
        val result = mutableMapOf<TestType, List<String>>()

        TestType.entries.filter { it != TestType.ALL }.forEach { testType ->
            result[testType] = generateTestTasks(modules, testType)
        }

        return result
    }

    /**
     * Generate task names for running tests
     */
    private fun generateTestTasks(modules: Set<String>, testType: TestType): List<String> {
        return modules.mapNotNull { modulePath ->
            val tasks = availableTestTasks[modulePath] ?: emptyList()

            // Generate task name based on test type and Android variant
            val taskName = when {
                // Android unit tests with variant
                testType == TestType.UNIT && androidUnitTestVariant.isNotEmpty() -> {
                    // Try to find exact match for the variant
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
                // Android UI/instrumented tests with variant
                (testType == TestType.UI || testType == TestType.E2E) && androidInstrumentedTestVariant.isNotEmpty() -> {
                    // Try to find exact match for the variant
                    val variantTaskName =
                        "connected${androidInstrumentedTestVariant.replaceFirstChar { it.uppercase() }}AndroidTest"
                    if (tasks.contains(variantTaskName)) {
                        variantTaskName
                    } else {
                        // Try to find any task that contains the variant name
                        tasks.find {
                            it.startsWith("connected") &&
                                    it.endsWith("AndroidTest") &&
                                    it.contains(androidInstrumentedTestVariant, ignoreCase = true)
                        } ?: testType.taskSuffix
                    }
                }
                // Default task name
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
}
