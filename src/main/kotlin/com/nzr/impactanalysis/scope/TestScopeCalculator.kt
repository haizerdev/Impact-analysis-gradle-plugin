package com.nzr.impactanalysis.scope

import com.nzr.impactanalysis.dependency.DependencyAnalyzer
import com.nzr.impactanalysis.dependency.ModuleDependencyGraph
import com.nzr.impactanalysis.extension.ImpactAnalysisExtension
import com.nzr.impactanalysis.model.ChangedFile
import com.nzr.impactanalysis.model.TestType
import org.gradle.api.Project

/**
 * Калькулятор scope тестов на основе изменений
 */
class TestScopeCalculator(
    private val rootProject: Project,
    private val dependencyGraph: ModuleDependencyGraph,
    private val dependencyAnalyzer: DependencyAnalyzer,
    private val extension: ImpactAnalysisExtension
) {

    /**
     * Рассчитать какие тесты нужно запустить
     */
    fun calculateTestScope(changedFiles: List<ChangedFile>): Map<TestType, List<String>> {
        val result = mutableMapOf<TestType, List<String>>()

        // Определяем затронутые модули
        val directlyAffectedModules = changedFiles.mapNotNull { it.module }.toSet()
        val allAffectedModules = dependencyGraph.getAffectedModules(directlyAffectedModules)

        // Проверяем критические файлы
        val criticalPaths = extension.criticalPaths.get()
        val hasCriticalChanges = changedFiles.any { file ->
            dependencyAnalyzer.isConfigFile(file.path) ||
                    criticalPaths.any { file.path.contains(it) }
        }

        if (hasCriticalChanges && extension.runAllTestsOnCriticalChanges) {
            // Запускаем все тесты во всех модулях
            return getAllTestsForModules(allAffectedModules)
        }

        // Определяем типы тестов для каждого затронутого модуля
        extension.testTypeRules.forEach { (testType, rule) ->
            val modulesToTest = when {
                // Если изменены файлы, соответствующие правилу
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

        // Если нет специфичных правил, запускаем unit тесты
        if (result.isEmpty() && extension.runUnitTestsByDefault) {
            result[TestType.UNIT] = generateTestTasks(allAffectedModules, TestType.UNIT)
        }

        return result
    }

    /**
     * Получить все тесты для модулей
     */
    private fun getAllTestsForModules(modules: Set<String>): Map<TestType, List<String>> {
        val result = mutableMapOf<TestType, List<String>>()

        TestType.values().filter { it != TestType.ALL }.forEach { testType ->
            result[testType] = generateTestTasks(modules, testType)
        }

        return result
    }

    /**
     * Сгенерировать имена задач для запуска тестов
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
     * Проверить, есть ли у модуля задача для данного типа тестов
     */
    private fun hasTestTask(project: Project, testType: TestType): Boolean {
        // Проверяем наличие задачи после evaluation
        return try {
            if (project.state.executed) {
                project.tasks.findByName(testType.taskSuffix) != null
            } else {
                // Если проект еще не evaluated, предполагаем что задача есть
                true
            }
        } catch (e: Exception) {
            true // В случае ошибки предполагаем что задача есть
        }
    }

    /**
     * Определить приоритетные модули для тестирования
     */
    fun getPriorityModules(changedFiles: List<ChangedFile>): List<String> {
        val moduleScores = mutableMapOf<String, Int>()

        changedFiles.forEach { file ->
            val module = file.module ?: return@forEach
            val score = moduleScores.getOrDefault(module, 0)

            // Увеличиваем приоритет в зависимости от типа изменения
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
