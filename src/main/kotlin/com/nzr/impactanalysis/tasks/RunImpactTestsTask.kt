package com.nzr.impactanalysis.tasks

import com.google.gson.Gson
import com.nzr.impactanalysis.model.ImpactAnalysisResult
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/**
 * Task for running tests based on impact analysis results
 */
abstract class RunImpactTestsTask : DefaultTask() {

    @get:InputFile
    abstract val impactResultFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val testTypes: Property<String>

    @get:Input
    abstract val continueOnFailure: Property<Boolean>

    init {
        group = "impact analysis"
        description = "Run tests based on impact analysis results"

        impactResultFile.convention(
            project.layout.buildDirectory.file("impact-analysis/result.json")
        )

        continueOnFailure.convention(false)
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

        logger.lifecycle("=".repeat(60))
        logger.lifecycle("Running Impact Tests")
        logger.lifecycle("=".repeat(60))

        val failedTasks = mutableListOf<String>()
        var totalTasks = 0
        var successfulTasks = 0

        testsToRun.forEach { (testType, tasks) ->
            logger.lifecycle("\n${testType.name} Tests (${tasks.size} tasks):")
            logger.lifecycle("-".repeat(60))

            tasks.forEach { taskPath ->
                totalTasks++
                logger.lifecycle("Running: $taskPath")

                try {
                    // Run task through Gradle
                    val taskResult = project.gradle.includedBuilds.firstOrNull()?.task(taskPath)
                        ?: project.rootProject.tasks.findByPath(taskPath)

                    if (taskResult != null) {
                        // Task found in current project
                        successfulTasks++
                        logger.lifecycle("✓ $taskPath completed successfully")
                    } else {
                        // Try running through exec
                        val execResult = project.exec { spec ->
                            spec.workingDir = project.rootProject.projectDir
                            if (System.getProperty("os.name").lowercase().contains("win")) {
                                spec.commandLine("cmd", "/c", "gradlew.bat", taskPath)
                            } else {
                                spec.commandLine("./gradlew", taskPath)
                            }
                            spec.isIgnoreExitValue = continueOnFailure.get()
                        }

                        if (execResult.exitValue == 0) {
                            successfulTasks++
                            logger.lifecycle("✓ $taskPath completed successfully")
                        } else {
                            failedTasks.add(taskPath)
                            logger.error("✗ $taskPath failed")

                            if (!continueOnFailure.get()) {
                                throw RuntimeException("Test task $taskPath failed")
                            }
                        }
                    }
                } catch (e: Exception) {
                    failedTasks.add(taskPath)
                    logger.error("✗ $taskPath failed: ${e.message}")

                    if (!continueOnFailure.get()) {
                        throw e
                    }
                }
            }
        }

        // Final report
        logger.lifecycle("\n" + "=".repeat(60))
        logger.lifecycle("Impact Tests Summary")
        logger.lifecycle("=".repeat(60))
        logger.lifecycle("Total tasks: $totalTasks")
        logger.lifecycle("Successful: $successfulTasks")
        logger.lifecycle("Failed: ${failedTasks.size}")

        if (failedTasks.isNotEmpty()) {
            logger.lifecycle("\nFailed tasks:")
            failedTasks.forEach { task ->
                logger.lifecycle("  ✗ $task")
            }

            if (!continueOnFailure.get()) {
                throw RuntimeException("${failedTasks.size} test task(s) failed")
            }
        }

        logger.lifecycle("=".repeat(60))
    }
}
