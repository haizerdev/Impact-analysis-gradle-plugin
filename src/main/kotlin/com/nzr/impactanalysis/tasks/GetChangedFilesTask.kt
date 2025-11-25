package com.nzr.impactanalysis.tasks

import com.nzr.impactanalysis.extension.ImpactAnalysisExtension
import com.nzr.impactanalysis.git.GitClient
import com.nzr.impactanalysis.git.GitDiffEntry
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/**
 * Task for getting list of changed files
 */
abstract class GetChangedFilesTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val baseBranch: Property<String>

    @get:Input
    @get:Optional
    abstract val compareBranch: Property<String>

    @get:Input
    abstract val includeUncommittedChanges: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val fileExtensions: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "impact analysis"
        description = "Get list of changed files from Git"

        outputFile.convention(
            project.layout.buildDirectory.file("impact-analysis/changed-files.txt")
        )

        // Disable up-to-date check, as task depends on Git state
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun execute() {
        val extension = project.extensions.getByType(ImpactAnalysisExtension::class.java)
        val gitClient = GitClient(project.rootProject.projectDir)

        try {
            // Get changes
            val changes = mutableListOf<GitDiffEntry>()

            if (includeUncommittedChanges.get()) {
                changes.addAll(gitClient.getUncommittedChanges())
            }

            val base = baseBranch.orNull ?: extension.baseBranch.get()
            val compare = compareBranch.orNull ?: extension.compareBranch.get()

            try {
                changes.addAll(gitClient.getChangedFiles(base, compare))
            } catch (e: Exception) {
                logger.warn("Failed to get changes: ${e.message}")
            }

            // Filter by extensions if specified
            val extensions = fileExtensions.orNull
            val filteredFiles = if (extensions != null && extensions.isNotEmpty()) {
                changes.filter { change ->
                    val fileExtension = change.newPath.substringAfterLast('.', "")
                    fileExtension in extensions
                }
            } else {
                changes
            }.map { it.newPath }

            logger.lifecycle("Found ${filteredFiles.size} changed files")

            // Save result
            val file = outputFile.get().asFile
            file.parentFile.mkdirs()

            if (filteredFiles.isEmpty()) {
                file.writeText("")
                logger.lifecycle("No changed files found")
            } else {
                file.writeText(filteredFiles.joinToString("\n"))
                logger.lifecycle("Changed files saved to: $file")

                filteredFiles.forEach { filePath ->
                    logger.lifecycle("  - $filePath")
                }
            }

        } finally {
            gitClient.close()
        }
    }
}
