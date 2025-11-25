package com.nzr.impactanalysis.dependency

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Граф зависимостей между модулями проекта
 */
class ModuleDependencyGraph(private val rootProject: Project) {

    private val dependencyMap = mutableMapOf<String, MutableSet<String>>()
    private val reverseDependencyMap = mutableMapOf<String, MutableSet<String>>()

    init {
        buildGraph()
    }

    /**
     * Построить граф зависимостей
     */
    private fun buildGraph() {
        rootProject.allprojects.forEach { project ->
            val modulePath = project.path
            dependencyMap.putIfAbsent(modulePath, mutableSetOf())

            // Анализируем все конфигурации
            project.configurations.forEach { config ->
                try {
                    config.dependencies
                        .filterIsInstance<ProjectDependency>()
                        .forEach { dependency ->
                            val dependencyPath = dependency.dependencyProject.path

                            // Прямая зависимость: project -> dependency
                            dependencyMap.getOrPut(modulePath) { mutableSetOf() }
                                .add(dependencyPath)

                            // Обратная зависимость: dependency <- project
                            reverseDependencyMap.getOrPut(dependencyPath) { mutableSetOf() }
                                .add(modulePath)
                        }
                } catch (e: Exception) {
                    // Некоторые конфигурации могут быть не resolvable
                    // Игнорируем ошибки
                }
            }
        }
    }

    /**
     * Получить все модули, которые зависят от данного модуля (прямо или косвенно)
     */
    fun getAffectedModules(changedModules: Set<String>): Set<String> {
        val affected = mutableSetOf<String>()
        val toProcess = changedModules.toMutableSet()

        while (toProcess.isNotEmpty()) {
            val current = toProcess.first()
            toProcess.remove(current)

            if (affected.add(current)) {
                // Добавляем все модули, которые зависят от текущего
                reverseDependencyMap[current]?.forEach { dependent ->
                    if (dependent !in affected) {
                        toProcess.add(dependent)
                    }
                }
            }
        }

        return affected
    }

    /**
     * Получить прямые зависимости модуля
     */
    fun getDirectDependencies(modulePath: String): Set<String> {
        return dependencyMap[modulePath]?.toSet() ?: emptySet()
    }

    /**
     * Получить модули, которые прямо зависят от данного модуля
     */
    fun getDirectDependents(modulePath: String): Set<String> {
        return reverseDependencyMap[modulePath]?.toSet() ?: emptySet()
    }

    /**
     * Получить все модули в проекте
     */
    fun getAllModules(): Set<String> {
        return dependencyMap.keys
    }

    /**
     * Экспортировать граф в DOT формат для визуализации
     */
    fun toDotFormat(): String {
        val sb = StringBuilder()
        sb.appendLine("digraph ModuleDependencies {")
        sb.appendLine("  rankdir=LR;")
        sb.appendLine("  node [shape=box];")

        dependencyMap.forEach { (module, dependencies) ->
            val moduleName = module.replace(":", "_").removePrefix("_")
            dependencies.forEach { dependency ->
                val depName = dependency.replace(":", "_").removePrefix("_")
                sb.appendLine("  \"$moduleName\" -> \"$depName\";")
            }
        }

        sb.appendLine("}")
        return sb.toString()
    }
}
