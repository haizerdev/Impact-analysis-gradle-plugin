package com.nzr.impactanalysis.extension

import com.nzr.impactanalysis.model.TestType
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Extension для конфигурации плагина Impact Analysis
 */
abstract class ImpactAnalysisExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Базовая ветка для сравнения (по умолчанию origin/main)
     */
    val baseBranch: Property<String> = objects.property(String::class.java).apply {
        convention("origin/main")
    }

    /**
     * Сравниваемая ветка (по умолчанию HEAD)
     */
    val compareBranch: Property<String> = objects.property(String::class.java).apply {
        convention("HEAD")
    }

    /**
     * Включить анализ uncommitted изменений
     */
    val includeUncommittedChanges: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(true)
    }

    /**
     * Запускать все тесты при изменении критических файлов
     */
    val runAllTestsOnCriticalChangesProperty: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(true)
    }

    /**
     * Запускать unit тесты по умолчанию если нет других правил
     */
    val runUnitTestsByDefaultProperty: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(true)
    }

    /**
     * Критические пути, при изменении которых запускаются все тесты
     */
    val criticalPaths: ListProperty<String> = objects.listProperty(String::class.java).apply {
        convention(
            listOf(
                "build.gradle",
                "build.gradle.kts",
                "settings.gradle",
                "settings.gradle.kts",
                "gradle.properties"
            )
        )
    }

    /**
     * Расширения файлов для линтинга
     */
    val lintFileExtensions: ListProperty<String> = objects.listProperty(String::class.java).apply {
        convention(listOf("kt", "java", "xml"))
    }

    /**
     * Правила для определения типов тестов
     */
    internal val testTypeRules = mutableMapOf<TestType, TestTypeRule>()

    /**
     * Настроить правила для типа тестов
     */
    fun testType(type: TestType, action: Action<TestTypeRule>) {
        val rule = testTypeRules.getOrPut(type) { TestTypeRule() }
        action.execute(rule)
    }

    /**
     * Вспомогательные методы для настройки типов тестов
     */
    fun unitTests(action: Action<TestTypeRule>) = testType(TestType.UNIT, action)
    fun integrationTests(action: Action<TestTypeRule>) = testType(TestType.INTEGRATION, action)
    fun uiTests(action: Action<TestTypeRule>) = testType(TestType.UI, action)
    fun e2eTests(action: Action<TestTypeRule>) = testType(TestType.E2E, action)
    fun apiTests(action: Action<TestTypeRule>) = testType(TestType.API, action)

    /**
     * Получить значение runAllTestsOnCriticalChanges
     */
    internal val runAllTestsOnCriticalChanges: Boolean
        get() = runAllTestsOnCriticalChangesProperty.get()

    /**
     * Получить значение runUnitTestsByDefault
     */
    internal val runUnitTestsByDefault: Boolean
        get() = runUnitTestsByDefaultProperty.get()
}

/**
 * Правило для определения когда запускать определенный тип тестов
 */
class TestTypeRule {
    /**
     * Паттерны путей файлов, при изменении которых нужно запустить эти тесты
     */
    val pathPatterns = mutableListOf<String>()

    /**
     * Запускать тесты только в измененных модулях (false = во всех зависимых модулях)
     */
    var runOnlyInChangedModules: Boolean = false

    /**
     * Добавить паттерн пути
     */
    fun whenChanged(pattern: String) {
        pathPatterns.add(pattern)
    }

    /**
     * Добавить несколько паттернов путей
     */
    fun whenChanged(vararg patterns: String) {
        pathPatterns.addAll(patterns)
    }

    /**
     * Проверить, соответствует ли файл правилу
     */
    fun shouldRunForFile(filePath: String): Boolean {
        if (pathPatterns.isEmpty()) {
            return true // Если нет паттернов, применяется ко всем файлам
        }

        // Нормализуем путь файла для сравнения (всегда используем /)
        val normalizedFilePath = filePath.replace("\\", "/")

        return pathPatterns.any { pattern ->
            // Нормализуем паттерн тоже
            val normalizedPattern = pattern.replace("\\", "/")

            when {
                // Паттерн типа **/something/** - ищем /something/ в любом месте пути
                normalizedPattern.startsWith("**/") && normalizedPattern.endsWith("/**") -> {
                    val middle = normalizedPattern.removePrefix("**/").removeSuffix("/**")
                    normalizedFilePath.contains("/$middle/")
                }

                normalizedPattern.startsWith("**/") -> {
                    val suffix = normalizedPattern.removePrefix("**/")
                    // **/*.kt - проверяем окончание всего пути
                    if (suffix.contains("*")) {
                        val regexPattern = suffix
                            .replace(".", "\\.")
                            .replace("*", "[^/]*")
                        normalizedFilePath.matches(".*/${regexPattern}".toRegex())
                    } else {
                        normalizedFilePath.endsWith(suffix) || normalizedFilePath.contains("/$suffix")
                    }
                }

                normalizedPattern.endsWith("/**") -> {
                    val prefix = normalizedPattern.removeSuffix("/**")
                    normalizedFilePath.startsWith(prefix) || normalizedFilePath.contains("/$prefix")
                }

                normalizedPattern.contains("*") -> {
                    // Если паттерн содержит / - это паттерн для пути
                    // Если нет / - это паттерн для имени файла
                    if (normalizedPattern.contains("/")) {
                        // Паттерн для пути - превращаем в regex
                        val regexPattern = normalizedPattern
                            .replace(".", "\\.")
                            .replace("**", "DOUBLE_STAR_PLACEHOLDER")
                            .replace("*", "[^/]*")
                            .replace("DOUBLE_STAR_PLACEHOLDER", ".*")
                        normalizedFilePath.matches(regexPattern.toRegex())
                    } else {
                        // Паттерн для имени файла - проверяем только имя
                        val fileName = normalizedFilePath.substringAfterLast("/")
                        val regexPattern = normalizedPattern
                            .replace(".", "\\.")
                            .replace("*", ".*")
                        fileName.matches(regexPattern.toRegex())
                    }
                }

                else -> {
                    normalizedFilePath.contains(normalizedPattern)
                }
            }
        }
    }
}
