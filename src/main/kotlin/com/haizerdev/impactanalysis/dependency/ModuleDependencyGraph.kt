package com.haizerdev.impactanalysis.dependency

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Dependency graph between project modules
 */
class ModuleDependencyGraph(private val rootProject: Project) {

    private val dependencyMap = mutableMapOf<String, MutableSet<String>>()
    private val reverseDependencyMap = mutableMapOf<String, MutableSet<String>>()

    init {
        buildGraph()
    }

    /**
     * Build dependency graph
     */
    private fun buildGraph() {
        rootProject.allprojects.forEach { project ->
            val modulePath = project.path
            dependencyMap.putIfAbsent(modulePath, mutableSetOf())

            // Analyze all configurations
            project.configurations.forEach { config ->
                try {
                    config.dependencies
                        .filterIsInstance<ProjectDependency>()
                        .forEach { dependency ->
                            val dependencyPath = dependency.dependencyProject.path

                            // Direct dependency: project -> dependency
                            dependencyMap.getOrPut(modulePath) { mutableSetOf() }
                                .add(dependencyPath)

                            // Reverse dependency: dependency <- project
                            reverseDependencyMap.getOrPut(dependencyPath) { mutableSetOf() }
                                .add(modulePath)
                        }
                } catch (e: Exception) {
                    // Some configurations may not be resolvable
                    // Ignore errors
                }
            }
        }
    }

    /**
     * Get all modules that depend on given module (directly or transitively)
     */
    fun getAffectedModules(changedModules: Set<String>): Set<String> {
        val affected = mutableSetOf<String>()
        val toProcess = changedModules.toMutableSet()

        while (toProcess.isNotEmpty()) {
            val current = toProcess.first()
            toProcess.remove(current)

            if (affected.add(current)) {
                // Add all modules that depend on current one
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
     * Get direct dependencies of module
     */
    fun getDirectDependencies(modulePath: String): Set<String> {
        return dependencyMap[modulePath]?.toSet() ?: emptySet()
    }

    /**
     * Get modules that directly depend on given module
     */
    fun getDirectDependents(modulePath: String): Set<String> {
        return reverseDependencyMap[modulePath]?.toSet() ?: emptySet()
    }

    /**
     * Get all modules in project
     */
    fun getAllModules(): Set<String> {
        return dependencyMap.keys
    }

    /**
     * Export graph to DOT format for visualization
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
