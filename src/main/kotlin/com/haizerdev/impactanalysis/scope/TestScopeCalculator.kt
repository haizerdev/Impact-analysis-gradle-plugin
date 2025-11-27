package com.haizerdev.impactanalysis.scope

import com.haizerdev.impactanalysis.dependency.DependencyAnalyzer
import com.haizerdev.impactanalysis.dependency.ModuleDependencyGraph
import com.haizerdev.impactanalysis.extension.ImpactAnalysisExtension
import com.haizerdev.impactanalysis.model.ChangedFile
import com.haizerdev.impactanalysis.model.TestType
import org.gradle.api.Project

/**
 * Test scope calculator based on changes
 */
class TestScopeCalculator(
    private val rootProject: Project,
    private val dependencyGraph: ModuleDependencyGraph,
    private val dependencyAnalyzer: DependencyAnalyzer,
    private val extension: ImpactAnalysisExtension
) {

    /**
     * Calculate which tests need to be run
     */
    fun calculateTestScope(changedFiles: List<ChangedFile>): Map<TestType, List<String>> {
        val result = mutableMapOf<TestType, List<String>>()

        // Determine affected modules
        val directlyAffectedModules = changedFiles.mapNotNull { it.module }.toSet()
        val allAffectedModules = dependencyGraph.getAffectedModules(directlyAffectedModules)

        // Check critical files
        val criticalPaths = extension.criticalPaths.get()
        val hasCriticalChanges = changedFiles.any { file ->
            dependencyAnalyzer.isConfigFile(file.path) ||
                    criticalPaths.any { file.path.contains(it) }
        }

        if (hasCriticalChanges && extension.runAllTestsOnCriticalChanges) {
            // Run all tests in all modules
            return getAllTestsForModules(allAffectedModules)
        }

        // Determine test types for each affected module
        extension.testTypeRules.forEach { (testType, rule) ->
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
        if (result.isEmpty() && extension.runUnitTestsByDefault) {
            result[TestType.UNIT] = generateTestTasks(allAffectedModules, TestType.UNIT)
        }

        return result
    }

    /**
     * Get all tests for modules
     */
    private fun getAllTestsForModules(modules: Set<String>): Map<TestType, List<String>> {
        val result = mutableMapOf<TestType, List<String>>()

        TestType.values().filter { it != TestType.ALL }.forEach { testType ->
            result[testType] = generateTestTasks(modules, testType)
        }

        return result
    }

    /**
     * Generate task names for running tests
     */
    private fun generateTestTasks(modules: Set<String>, testType: TestType): List<String> {
        return modules.mapNotNull { modulePath ->
            val project = rootProject.findProject(modulePath)
            if (project != null && hasTestTask(project, testType)) {
                if (modulePath == ":") {
                    testType.taskSuffix
                } else {
                    "$modulePath:${testType.taskSuffix}"
                }
            } else {
                null
            }
        }.sorted()
    }

    /**
     * Check if module has a task for given test type
     */
    private fun hasTestTask(project: Project, testType: TestType): Boolean {
        // Check for task presence after evaluation
        return try {
            if (project.state.executed) {
                project.tasks.findByName(testType.taskSuffix) != null
            } else {
                // If project not yet evaluated, assume task exists
                true
            }
        } catch (e: Exception) {
            true // In case of error, assume task exists
        }
    }

    /**
     * Determine priority modules for testing
     */
    fun getPriorityModules(changedFiles: List<ChangedFile>): List<String> {
        val moduleScores = mutableMapOf<String, Int>()

        changedFiles.forEach { file ->
            val module = file.module ?: return@forEach
            val score = moduleScores.getOrDefault(module, 0)

            // Increase priority based on change type
            val increment = when {
                dependencyAnalyzer.isTestFile(file.path) -> 1
                dependencyAnalyzer.isConfigFile(file.path) -> 5
                file.path.contains("/src/main/") -> 3
                else -> 2
            }

            moduleScores[module] = score + increment
        }

        return moduleScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }
}
