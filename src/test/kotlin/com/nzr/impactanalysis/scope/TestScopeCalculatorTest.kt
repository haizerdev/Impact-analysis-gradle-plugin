package com.nzr.impactanalysis.scope

import com.nzr.impactanalysis.dependency.DependencyAnalyzer
import com.nzr.impactanalysis.dependency.ModuleDependencyGraph
import com.nzr.impactanalysis.extension.ImpactAnalysisExtension
import com.nzr.impactanalysis.model.ChangedFile
import com.nzr.impactanalysis.model.ChangeType
import com.nzr.impactanalysis.model.FileLanguage
import com.nzr.impactanalysis.model.TestType
import com.nzr.impactanalysis.scope.TestScopeCalculator
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit тесты для TestScopeCalculator
 */
class TestScopeCalculatorTest {

    private lateinit var rootProject: Project
    private lateinit var extension: ImpactAnalysisExtension
    private lateinit var dependencyGraph: ModuleDependencyGraph
    private lateinit var dependencyAnalyzer: DependencyAnalyzer
    private lateinit var calculator: TestScopeCalculator

    @Before
    fun setup() {
        rootProject = ProjectBuilder.builder().withName("root").build()

        // Создаем модули
        val app = ProjectBuilder.builder()
            .withName("app")
            .withParent(rootProject)
            .build()

        val feature = ProjectBuilder.builder()
            .withName("feature")
            .withParent(rootProject)
            .build()

        // Применяем плагины
        app.pluginManager.apply("java")
        feature.pluginManager.apply("java")

        // Настраиваем зависимости
        app.dependencies.add("implementation", feature)

        // Создаем extension
        extension = rootProject.extensions.create(
            "impactAnalysis",
            ImpactAnalysisExtension::class.java
        )

        // Настраиваем правила
        extension.unitTests { rule ->
            rule.whenChanged("src/main/**")
            rule.runOnlyInChangedModules = false
        }

        extension.integrationTests { rule ->
            rule.whenChanged("**/repository/**", "**/database/**")
            rule.runOnlyInChangedModules = true
        }

        dependencyGraph = ModuleDependencyGraph(rootProject)
        dependencyAnalyzer = DependencyAnalyzer(rootProject)

        calculator = TestScopeCalculator(
            rootProject,
            dependencyGraph,
            dependencyAnalyzer,
            extension
        )
    }

    @Test
    fun `test calculateTestScope with main code changes runs unit tests`() {
        val changedFiles = listOf(
            ChangedFile(
                path = "feature/src/main/kotlin/Feature.kt",
                module = ":feature",
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.KOTLIN
            )
        )

        val scope = calculator.calculateTestScope(changedFiles)

        assertTrue(scope.containsKey(TestType.UNIT))
        val unitTests = scope[TestType.UNIT]!!
        assertTrue(unitTests.contains(":feature:test"))
        assertTrue(unitTests.contains(":app:test"))
    }

    @Test
    fun `test calculateTestScope with repository changes runs integration tests`() {
        val changedFiles = listOf(
            ChangedFile(
                path = "feature/src/main/kotlin/repository/UserRepository.kt",
                module = ":feature",
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.KOTLIN
            )
        )

        val scope = calculator.calculateTestScope(changedFiles)

        assertTrue(scope.containsKey(TestType.INTEGRATION))
        val integrationTests = scope[TestType.INTEGRATION]!!
        assertTrue(integrationTests.contains(":feature:integrationTest"))
    }

    @Test
    fun `test calculateTestScope with config changes runs all tests when enabled`() {
        extension.runAllTestsOnCriticalChangesProperty.set(true)
        extension.criticalPaths.set(listOf("build.gradle"))

        val changedFiles = listOf(
            ChangedFile(
                path = "build.gradle",
                module = null,
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.GROOVY
            )
        )

        val scope = calculator.calculateTestScope(changedFiles)

        // Должны быть все типы тестов
        assertTrue(scope.isNotEmpty())
    }

    @Test
    fun `test calculateTestScope returns empty when no rules match`() {
        // Отключаем default unit tests
        extension.runUnitTestsByDefaultProperty.set(false)

        val changedFiles = listOf(
            ChangedFile(
                path = "README.md",
                module = null,
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.UNKNOWN
            )
        )

        val scope = calculator.calculateTestScope(changedFiles)

        assertTrue(scope.isEmpty() || scope.all { it.value.isEmpty() })
    }

    @Test
    fun `test getPriorityModules ranks modules by changes`() {
        val changedFiles = listOf(
            ChangedFile(
                path = "feature/src/main/kotlin/Feature.kt",
                module = ":feature",
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.KOTLIN
            ),
            ChangedFile(
                path = "feature/src/main/kotlin/Feature2.kt",
                module = ":feature",
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.KOTLIN
            ),
            ChangedFile(
                path = "app/src/main/kotlin/App.kt",
                module = ":app",
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.KOTLIN
            )
        )

        val priority = calculator.getPriorityModules(changedFiles)

        // feature должен быть первым (больше изменений)
        assertEquals(":feature", priority[0])
    }

    @Test
    fun `test getPriorityModules prioritizes config files`() {
        val changedFiles = listOf(
            ChangedFile(
                path = "feature/src/main/kotlin/Feature.kt",
                module = ":feature",
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.KOTLIN
            ),
            ChangedFile(
                path = "app/build.gradle",
                module = ":app",
                changeType = ChangeType.MODIFIED,
                language = FileLanguage.GROOVY
            )
        )

        val priority = calculator.getPriorityModules(changedFiles)

        // app должен быть первым (изменен конфиг файл)
        assertEquals(":app", priority[0])
    }
}
