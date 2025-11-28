package com.haizerdev.impactanalysis

import com.haizerdev.impactanalysis.extension.ImpactAnalysisExtension
import com.haizerdev.impactanalysis.tasks.CalculateImpactTask
import com.haizerdev.impactanalysis.tasks.GetChangedFilesTask
import com.haizerdev.impactanalysis.tasks.RunImpactKotlinCompileTask
import com.haizerdev.impactanalysis.tasks.RunImpactTestsTask
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
            task.rootProjectDir.convention(project.layout.projectDirectory)
            task.lintFileExtensions.convention(extension.lintFileExtensions)
            task.runAllTestsOnCriticalChanges.convention(extension.runAllTestsOnCriticalChanges)
            task.runUnitTestsByDefault.convention(extension.runUnitTestsByDefault)
            task.criticalPaths.convention(extension.criticalPaths)

            // Android variant configuration
            task.androidUnitTestVariant.convention(extension.androidUnitTestVariant)
            task.androidInstrumentedTestVariant.convention(extension.androidInstrumentedTestVariant)

            // Serialize module dependency data during configuration
            task.moduleDependencies.convention(project.provider {
                serializeModuleDependencies(project.rootProject)
            })

            task.moduleReverseDependencies.convention(project.provider {
                serializeModuleReverseDependencies(project.rootProject)
            })

            task.allModules.convention(project.provider {
                project.rootProject.allprojects.map { it.path }.toList()
            })

            task.moduleDirectories.convention(project.provider {
                serializeModuleDirectories(project.rootProject)
            })

            task.availableTestTasks.convention(project.provider {
                serializeAvailableTestTasks(project.rootProject)
            })

            // Convert test type rules to serializable format
            task.testTypeRulesData.convention(project.provider {
                extension.testTypeRulesMap.mapKeys { it.key.name }
                    .mapValues { (_, rule) ->
                        com.haizerdev.impactanalysis.tasks.SerializableTestTypeRule(
                            pathPatterns = rule.pathPatterns.toList(),
                            runOnlyInChangedModules = rule.runOnlyInChangedModules
                        )
                    }
            })
        }

        // Task for getting changed files
        project.tasks.register("getChangedFiles", GetChangedFilesTask::class.java) { task ->
            task.description = "Get list of changed files from Git"
            task.group = "impact analysis"

            task.baseBranch.convention(extension.baseBranch)
            task.compareBranch.convention(extension.compareBranch)
            task.includeUncommittedChanges.convention(extension.includeUncommittedChanges)
            task.rootProjectDir.convention(project.layout.projectDirectory)
        }

        // Task for getting changed files for linting
        project.tasks.register("getChangedFilesForLint", GetChangedFilesTask::class.java) { task ->
            task.description = "Get list of changed files for linting"
            task.group = "impact analysis"

            task.baseBranch.convention(extension.baseBranch)
            task.compareBranch.convention(extension.compareBranch)
            task.includeUncommittedChanges.convention(extension.includeUncommittedChanges)
            task.rootProjectDir.convention(project.layout.projectDirectory)
            task.fileExtensions.convention(extension.lintFileExtensions)
            task.outputFile.convention(
                project.layout.buildDirectory.file("impact-analysis/lint-files.txt")
            )
        }

        // Task for running tests based on impact analysis
        project.tasks.register("runImpactTests", RunImpactTestsTask::class.java) { task ->
            task.description = "Run tests based on impact analysis results"
            task.group = "impact analysis"

            task.rootProjectDir.convention(project.layout.projectDirectory)

            // Depends on calculateImpact
            task.dependsOn("calculateImpact")
        }

        // Task for running Kotlin compilation based on impact analysis
        project.tasks.register("runImpactKotlinCompile", RunImpactKotlinCompileTask::class.java) { task ->
            task.description = "Run Kotlin compilation for affected modules"
            task.group = "impact analysis"

            task.rootProjectDir.convention(project.layout.projectDirectory)

            // Depends on calculateImpact
            task.dependsOn("calculateImpact")
        }
    }

    private fun serializeModuleDependencies(rootProject: Project): Map<String, List<String>> {
        val dependencyMap = mutableMapOf<String, MutableList<String>>()

        rootProject.allprojects.forEach { project ->
            val modulePath = project.path
            dependencyMap.putIfAbsent(modulePath, mutableListOf())

            // Analyze all configurations
            project.configurations.forEach { config ->
                try {
                    config.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .forEach { dependency ->
                            val dependencyPath = dependency.dependencyProject.path
                            dependencyMap.getOrPut(modulePath) { mutableListOf() }
                                .add(dependencyPath)
                        }
                } catch (e: Exception) {
                    // Some configurations may not be resolvable, ignore
                }
            }
        }

        return dependencyMap.mapValues { it.value.distinct() }
    }

    private fun serializeModuleReverseDependencies(rootProject: Project): Map<String, List<String>> {
        val reverseDependencyMap = mutableMapOf<String, MutableList<String>>()

        rootProject.allprojects.forEach { project ->
            val modulePath = project.path

            // Analyze all configurations
            project.configurations.forEach { config ->
                try {
                    config.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .forEach { dependency ->
                            val dependencyPath = dependency.dependencyProject.path
                            // Reverse dependency: dependencyPath <- modulePath
                            reverseDependencyMap.getOrPut(dependencyPath) { mutableListOf() }
                                .add(modulePath)
                        }
                } catch (e: Exception) {
                    // Some configurations may not be resolvable, ignore
                }
            }
        }

        return reverseDependencyMap.mapValues { it.value.distinct() }
    }

    private fun serializeModuleDirectories(rootProject: Project): Map<String, String> {
        return rootProject.allprojects.associate { project ->
            val relativePath = project.projectDir.relativeTo(rootProject.projectDir).path
            project.path to if (relativePath.isEmpty() || relativePath == ".") "" else relativePath
        }
    }

    private fun serializeAvailableTestTasks(rootProject: Project): Map<String, List<String>> {
        return rootProject.allprojects.associate { project ->
            // Get test task names that might be available
            // We check for task names, not task objects, to avoid configuration issues
            val testTaskNames = mutableListOf<String>()

            // Common test task names
            val commonTestTasks = listOf("test", "integrationTest", "uiTest", "e2eTest", "apiTest")

            try {
                // Get all task names from the project
                val allTaskNames = project.tasks.names

                // Filter tasks that match test patterns
                allTaskNames.forEach { taskName ->
                    val isTestTask = commonTestTasks.contains(taskName) ||
                            taskName.matches(Regex("test.*UnitTest")) ||           // Android unit tests
                            taskName.matches(Regex("connected.*AndroidTest")) ||  // Android instrumented tests
                            taskName.endsWith("Test")                              // Generic test tasks

                    if (isTestTask) {
                        testTaskNames.add(taskName)
                    }
                }
            } catch (e: Exception) {
                // If we can't get task names, fall back to checking common tasks
                commonTestTasks.forEach { taskName ->
                    try {
                        if (project.tasks.findByName(taskName) != null) {
                            testTaskNames.add(taskName)
                        }
                    } catch (ex: Exception) {
                        // Task might not be configured yet
                    }
                }
            }

            project.path to testTaskNames.distinct()
        }
    }
}
