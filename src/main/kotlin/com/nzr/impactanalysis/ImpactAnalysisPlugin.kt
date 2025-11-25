package com.nzr.impactanalysis

import com.nzr.impactanalysis.extension.ImpactAnalysisExtension
import com.nzr.impactanalysis.tasks.CalculateImpactTask
import com.nzr.impactanalysis.tasks.GetChangedFilesTask
import com.nzr.impactanalysis.tasks.RunImpactTestsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Плагин для анализа изменений и определения scope тестов
 */
class ImpactAnalysisPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Применяем плагин только к root проекту
        if (project != project.rootProject) {
            return
        }

        // Регистрируем extension
        val extension = project.extensions.create(
            "impactAnalysis",
            ImpactAnalysisExtension::class.java
        )

        // Регистрируем задачи
        registerTasks(project, extension)

        project.logger.lifecycle("Impact Analysis Plugin applied to project: ${project.name}")
    }

    private fun registerTasks(project: Project, extension: ImpactAnalysisExtension) {
        // Задача расчета impact analysis
        project.tasks.register("calculateImpact", CalculateImpactTask::class.java) { task ->
            task.description = "Calculate impact analysis based on Git changes"
            task.group = "impact analysis"

            task.baseBranch.convention(extension.baseBranch)
            task.compareBranch.convention(extension.compareBranch)
            task.includeUncommittedChanges.convention(extension.includeUncommittedChanges)
        }

        // Задача получения измененных файлов
        project.tasks.register("getChangedFiles", GetChangedFilesTask::class.java) { task ->
            task.description = "Get list of changed files from Git"
            task.group = "impact analysis"

            task.baseBranch.convention(extension.baseBranch)
            task.compareBranch.convention(extension.compareBranch)
            task.includeUncommittedChanges.convention(extension.includeUncommittedChanges)
        }

        // Задача для получения измененных файлов для линтинга
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

        // Задача запуска тестов на основе impact analysis
        project.tasks.register("runImpactTests", RunImpactTestsTask::class.java) { task ->
            task.description = "Run tests based on impact analysis results"
            task.group = "impact analysis"

            // Зависит от calculateImpact
            task.dependsOn("calculateImpact")
        }

        // Задача для полного flow: analyze + test
        project.tasks.register("impactTest") { task ->
            task.description = "Calculate impact and run affected tests"
            task.group = "impact analysis"

            task.dependsOn("runImpactTests")
        }
    }
}
