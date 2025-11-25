package com.nzr.impactanalysis

import com.nzr.impactanalysis.extension.ImpactAnalysisExtension
import com.nzr.impactanalysis.tasks.CalculateImpactTask
import com.nzr.impactanalysis.tasks.GetChangedFilesTask
import com.nzr.impactanalysis.tasks.RunImpactTestsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin for change analysis and test scope determination
 */
class ImpactAnalysisPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Apply plugin only to root project
        if (project != project.rootProject) {
            return
        }

        // Register extension
        val extension = project.extensions.create(
            "impactAnalysis",
            ImpactAnalysisExtension::class.java
        )

        // Register tasks
        registerTasks(project, extension)

        project.logger.lifecycle("Impact Analysis Plugin applied to project: ${project.name}")
    }

    private fun registerTasks(project: Project, extension: ImpactAnalysisExtension) {
        // Task for calculating impact analysis
        project.tasks.register("calculateImpact", CalculateImpactTask::class.java) { task ->
            task.description = "Calculate impact analysis based on Git changes"
            task.group = "impact analysis"

            task.baseBranch.convention(extension.baseBranch)
            task.compareBranch.convention(extension.compareBranch)
            task.includeUncommittedChanges.convention(extension.includeUncommittedChanges)
        }

        // Task for getting changed files
        project.tasks.register("getChangedFiles", GetChangedFilesTask::class.java) { task ->
            task.description = "Get list of changed files from Git"
            task.group = "impact analysis"

            task.baseBranch.convention(extension.baseBranch)
            task.compareBranch.convention(extension.compareBranch)
            task.includeUncommittedChanges.convention(extension.includeUncommittedChanges)
        }

        // Task for getting changed files for linting
        project.tasks.register("getChangedFilesForLint", GetChangedFilesTask::class.java) { task ->
            task.description = "Get list of changed files for linting"
            task.group = "impact analysis"

            task.baseBranch.convention(extension.baseBranch)
            task.compareBranch.convention(extension.compareBranch)
            task.includeUncommittedChanges.convention(extension.includeUncommittedChanges)
            task.fileExtensions.convention(extension.lintFileExtensions)
            task.outputFile.convention(
                project.layout.buildDirectory.file("impact-analysis/lint-files.txt")
            )
        }

        // Task for running tests based on impact analysis
        project.tasks.register("runImpactTests", RunImpactTestsTask::class.java) { task ->
            task.description = "Run tests based on impact analysis results"
            task.group = "impact analysis"

            // Depends on calculateImpact
            task.dependsOn("calculateImpact")
        }

        // Task for complete flow: analyze + test
        project.tasks.register("impactTest") { task ->
            task.description = "Calculate impact and run affected tests"
            task.group = "impact analysis"

            task.dependsOn("runImpactTests")
        }
    }
}
