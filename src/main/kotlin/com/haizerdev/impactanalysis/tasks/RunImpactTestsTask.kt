package com.haizerdev.impactanalysis.tasks

import com.google.gson.Gson
import com.haizerdev.impactanalysis.model.ImpactAnalysisResult
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Task for running tests based on impact analysis results
 * Configuration cache compatible - uses ExecOperations instead of project.exec
 */
abstract class RunImpactTestsTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:InputFile
    abstract val impactResultFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val testTypes: Property<String>

    @get:Input
    abstract val continueOnFailure: Property<Boolean>

    @get:Internal
    abstract val rootProjectDir: DirectoryProperty

    private val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    init {
        group = "impact analysis"
        description = "Run tests based on impact analysis results"

        impactResultFile.convention(
            project.layout.buildDirectory.file("impact-analysis/result.json")
        )

        continueOnFailure.convention(false)

        rootProjectDir.convention(project.layout.projectDirectory)
    }

    @TaskAction
    fun execute() {
        val resultFile = impactResultFile.get().asFile

        if (!resultFile.exists()) {
            logger.error("Impact analysis result file not found: $resultFile")
            logger.error("Run 'calculateImpact' task first")
            throw IllegalStateException("Impact analysis result not found")
        }

        // Read result
        val gson = Gson()
        val result = gson.fromJson(resultFile.readText(), ImpactAnalysisResult::class.java)

        if (result.testsToRun.isEmpty()) {
            logger.lifecycle("No tests to run")
            return
        }

        // Filter by test types if specified
        val requestedTypes = testTypes.orNull?.split(",")?.map { it.trim() }
        val testsToRun = if (requestedTypes != null) {
            result.testsToRun.filterKeys { type ->
                requestedTypes.any { it.equals(type.name, ignoreCase = true) }
            }
        } else {
            result.testsToRun
        }

        // Collect all test tasks to run in parallel (use Set to avoid duplicates)
        val allTestTasks = mutableSetOf<String>()

        logger.lifecycle("=".repeat(60))
        logger.lifecycle("Running Impact Tests (Parallel Execution)")
        logger.lifecycle("=".repeat(60))

        testsToRun.forEach { (testType, tasks) ->
            logger.lifecycle("\n${testType.name} Tests (${tasks.size} tasks):")
            logger.lifecycle("-".repeat(60))
            tasks.forEach { task ->
                logger.lifecycle("  - $task")
                allTestTasks.add(task)
            }
        }

        if (allTestTasks.isEmpty()) {
            logger.lifecycle("No tests to run")
            return
        }

        // Sort for consistent output
        val sortedTasks = allTestTasks.sorted()

        logger.lifecycle("\n" + "=".repeat(60))
        logger.lifecycle("Executing ${sortedTasks.size} unique test task(s) in parallel...")
        logger.lifecycle("=".repeat(60))

        try {
            // Run all tasks in a single Gradle command (parallel execution)
            val command = if (isWindows) {
                listOf("cmd", "/c", "gradlew.bat") + sortedTasks + listOf("--continue", "--no-daemon", "--parallel")
            } else {
                listOf("./gradlew") + sortedTasks + listOf("--continue", "--no-daemon", "--parallel")
            }

            val execResult = execOperations.exec { spec ->
                spec.workingDir = rootProjectDir.get().asFile
                spec.commandLine(command)
                spec.isIgnoreExitValue = continueOnFailure.get()
            }

            // Final report
            logger.lifecycle("\n" + "=".repeat(60))
            logger.lifecycle("Impact Tests Summary")
            logger.lifecycle("=".repeat(60))

            if (execResult.exitValue == 0) {
                logger.lifecycle("✓ All ${sortedTasks.size} test task(s) completed successfully")
            } else {
                logger.lifecycle("⚠ Some tests failed (exit code: ${execResult.exitValue})")
                logger.lifecycle("Run with --continue flag allows all tests to execute")

                if (!continueOnFailure.get()) {
                    throw RuntimeException("Some test tasks failed. See build output for details.")
                }
            }

            logger.lifecycle("=".repeat(60))

        } catch (e: Exception) {
            logger.error("✗ Test execution failed: ${e.message}")

            if (!continueOnFailure.get()) {
                throw e
            }
        }
    }
}
