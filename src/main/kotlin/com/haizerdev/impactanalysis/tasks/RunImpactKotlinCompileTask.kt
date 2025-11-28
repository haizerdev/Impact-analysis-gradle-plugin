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
 * Task for running Kotlin compilation based on impact analysis results
 * Configuration cache compatible - uses ExecOperations instead of project.exec
 */
abstract class RunImpactKotlinCompileTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:InputFile
    abstract val impactResultFile: RegularFileProperty

    @get:Input
    abstract val continueOnFailure: Property<Boolean>

    @get:Internal
    abstract val rootProjectDir: DirectoryProperty

    private val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    init {
        group = "impact analysis"
        description = "Run Kotlin compilation based on impact analysis results"

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

        if (result.affectedModules.isEmpty()) {
            logger.lifecycle("No modules affected - skipping compilation")
            return
        }

        logger.lifecycle("=".repeat(60))
        logger.lifecycle("Running Kotlin Compilation for Affected Modules (Parallel)")
        logger.lifecycle("=".repeat(60))

        // Collect all compilation tasks
        val compileTasks = result.affectedModules.map { modulePath ->
            if (modulePath == ":") {
                ":compileKotlin"
            } else {
                "$modulePath:compileKotlin"
            }
        }

        compileTasks.forEach { task ->
            logger.lifecycle("  - $task")
        }

        logger.lifecycle("\n" + "=".repeat(60))
        logger.lifecycle("Executing ${compileTasks.size} compilation task(s) in parallel...")
        logger.lifecycle("=".repeat(60))

        try {
            // Run all tasks in a single Gradle command (parallel execution)
            val command = if (isWindows) {
                listOf("cmd", "/c", "gradlew.bat") + compileTasks + listOf("--continue", "--no-daemon")
            } else {
                listOf("./gradlew") + compileTasks + listOf("--continue", "--no-daemon")
            }

            val execResult = execOperations.exec { spec ->
                spec.workingDir = rootProjectDir.get().asFile
                spec.commandLine(command)
                spec.isIgnoreExitValue = continueOnFailure.get()
            }

            // Final report
            logger.lifecycle("\n" + "=".repeat(60))
            logger.lifecycle("Kotlin Compilation Summary")
            logger.lifecycle("=".repeat(60))

            if (execResult.exitValue == 0) {
                logger.lifecycle("✓ All ${compileTasks.size} compilation task(s) completed successfully")
            } else {
                logger.lifecycle("⚠ Some compilation tasks failed (exit code: ${execResult.exitValue})")

                if (!continueOnFailure.get()) {
                    throw RuntimeException("Some compilation tasks failed. See build output for details.")
                }
            }

            logger.lifecycle("=".repeat(60))

        } catch (e: Exception) {
            logger.error("✗ Compilation failed: ${e.message}")

            if (!continueOnFailure.get()) {
                throw e
            }
        }
    }
}
