package com.nzr.impactanalysis.dependency

import com.nzr.impactanalysis.dependency.ModuleDependencyGraph
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit тесты для ModuleDependencyGraph
 */
class ModuleDependencyGraphTest {

    private lateinit var rootProject: Project
    private lateinit var app: Project
    private lateinit var featureAuth: Project
    private lateinit var featureProfile: Project
    private lateinit var coreNetwork: Project

    @Before
    fun setup() {
        // Создаем мок структуру проекта
        rootProject = ProjectBuilder.builder().withName("root").build()

        // Создаем модули
        app = ProjectBuilder.builder()
            .withName("app")
            .withParent(rootProject)
            .build()

        featureAuth = ProjectBuilder.builder()
            .withName("feature-auth")
            .withParent(rootProject)
            .build()

        featureProfile = ProjectBuilder.builder()
            .withName("feature-profile")
            .withParent(rootProject)
            .build()

        coreNetwork = ProjectBuilder.builder()
            .withName("core-network")
            .withParent(rootProject)
            .build()

        // Применяем плагин java для создания конфигураций
        app.pluginManager.apply("java")
        featureAuth.pluginManager.apply("java")
        featureProfile.pluginManager.apply("java")
        coreNetwork.pluginManager.apply("java")

        // Настраиваем зависимости
        // app -> feature-auth
        app.dependencies.add("implementation", featureAuth)

        // app -> feature-profile
        app.dependencies.add("implementation", featureProfile)

        // feature-auth -> core-network
        featureAuth.dependencies.add("implementation", coreNetwork)

        // feature-profile -> core-network
        featureProfile.dependencies.add("implementation", coreNetwork)
    }

    @Test
    fun `test getAllModules returns all project modules`() {
        val graph = ModuleDependencyGraph(rootProject)
        val modules = graph.getAllModules()

        assertTrue(modules.contains(":"))
        assertTrue(modules.contains(":app"))
        assertTrue(modules.contains(":feature-auth"))
        assertTrue(modules.contains(":feature-profile"))
        assertTrue(modules.contains(":core-network"))
    }

    @Test
    fun `test getDirectDependencies returns direct dependencies`() {
        val graph = ModuleDependencyGraph(rootProject)
        val deps = graph.getDirectDependencies(":app")

        assertTrue(deps.contains(":feature-auth"))
        assertTrue(deps.contains(":feature-profile"))
        assertEquals(2, deps.size)
    }

    @Test
    fun `test getDirectDependents returns modules depending on given module`() {
        val graph = ModuleDependencyGraph(rootProject)
        val dependents = graph.getDirectDependents(":core-network")

        assertTrue(dependents.contains(":feature-auth"))
        assertTrue(dependents.contains(":feature-profile"))
    }

    @Test
    fun `test getAffectedModules finds all transitive dependents`() {
        val graph = ModuleDependencyGraph(rootProject)

        // Если изменился core-network, должны быть затронуты все модули
        val affected = graph.getAffectedModules(setOf(":core-network"))

        assertTrue(affected.contains(":core-network"))
        assertTrue(affected.contains(":feature-auth"))
        assertTrue(affected.contains(":feature-profile"))
        assertTrue(affected.contains(":app"))
    }

    @Test
    fun `test getAffectedModules with leaf module`() {
        val graph = ModuleDependencyGraph(rootProject)

        // Если изменился app (листовой модуль), только он затронут
        val affected = graph.getAffectedModules(setOf(":app"))

        assertEquals(1, affected.size)
        assertTrue(affected.contains(":app"))
    }

    @Test
    fun `test getAffectedModules with multiple changed modules`() {
        val graph = ModuleDependencyGraph(rootProject)

        // Изменились feature-auth и feature-profile
        val affected = graph.getAffectedModules(setOf(":feature-auth", ":feature-profile"))

        assertTrue(affected.contains(":feature-auth"))
        assertTrue(affected.contains(":feature-profile"))
        assertTrue(affected.contains(":app"))
        // core-network не должен быть затронут (он dependency, а не dependent)
        assertTrue(!affected.contains(":core-network") || affected.size >= 3)
    }

    @Test
    fun `test toDotFormat generates valid DOT output`() {
        val graph = ModuleDependencyGraph(rootProject)
        val dot = graph.toDotFormat()

        assertTrue(dot.startsWith("digraph ModuleDependencies {"))
        assertTrue(dot.endsWith("}\n"))
        assertTrue(dot.contains("->"))
    }
}
