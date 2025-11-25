package com.nzr.impactanalysis.tasks

import com.google.gson.GsonBuilder
import com.nzr.impactanalysis.dependency.DependencyAnalyzer
import com.nzr.impactanalysis.dependency.ModuleDependencyGraph
import com.nzr.impactanalysis.extension.ImpactAnalysisExtension
import com.nzr.impactanalysis.git.GitClient
import com.nzr.impactanalysis.model.ChangedFile
import com.nzr.impactanalysis.model.FileLanguage
import com.nzr.impactanalysis.model.ImpactAnalysisResult
import com.nzr.impactanalysis.scope.TestScopeCalculator
import com.nzr.impactanalysis.git.GitDiffEntry
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/**
 * Task for calculating impact analysis
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
        val extension = project.extensions.getByType(ImpactAnalysisExtension::class.java)

        // Create components
        val gitClient = GitClient(project.rootProject.projectDir)
        val dependencyGraph = ModuleDependencyGraph(project.rootProject)
        val dependencyAnalyzer = DependencyAnalyzer(project.rootProject)
        val testScopeCalculator = TestScopeCalculator(
            project.rootProject,
            dependencyGraph,
            dependencyAnalyzer,
            extension
        )

        try {
            // Get changes from Git
            val gitChanges = mutableListOf<GitDiffEntry>()

            if (includeUncommittedChanges.get()) {
                gitChanges.addAll(gitClient.getUncommittedChanges())
            }

            val base = baseBranch.orNull ?: extension.baseBranch.get()
            val compare = compareBranch.orNull ?: extension.compareBranch.get()

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
            val affectedModules = dependencyGraph.getAffectedModules(directModules)

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
            val lintExtensions = extension.lintFileExtensions.get()
            val filesToLint = changedFiles
                .filter { file ->
                    val extension = file.path.substringAfterLast('.', "")
                    extension in lintExtensions
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
