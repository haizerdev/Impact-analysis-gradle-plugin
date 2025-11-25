package com.nzr.impactanalysis.extension

import com.nzr.impactanalysis.model.TestType
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Extension for configuring Impact Analysis plugin
 */
abstract class ImpactAnalysisExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Base branch for comparison (default: origin/main)
     */
    val baseBranch: Property<String> = objects.property(String::class.java).apply {
        convention("origin/main")
    }

    /**
     * Branch to compare (default: HEAD)
     */
    val compareBranch: Property<String> = objects.property(String::class.java).apply {
        convention("HEAD")
    }

    /**
     * Include uncommitted changes analysis
     */
    val includeUncommittedChanges: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(true)
    }

    /**
     * Run all tests when critical files are changed
     */
    val runAllTestsOnCriticalChangesProperty: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(true)
    }

    /**
     * Run unit tests by default if no other rules match
     */
    val runUnitTestsByDefaultProperty: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(true)
    }

    /**
     * Critical paths that trigger all tests when changed
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
     * File extensions for linting
     */
    val lintFileExtensions: ListProperty<String> = objects.listProperty(String::class.java).apply {
        convention(listOf("kt", "java", "xml"))
    }

    /**
     * Rules for determining test types
     */
    internal val testTypeRules = mutableMapOf<TestType, TestTypeRule>()

    /**
     * Configure rules for test type
     */
    fun testType(type: TestType, action: Action<TestTypeRule>) {
        val rule = testTypeRules.getOrPut(type) { TestTypeRule() }
        action.execute(rule)
    }

    /**
     * Helper methods for configuring test types
     */
    fun unitTests(action: Action<TestTypeRule>) = testType(TestType.UNIT, action)
    fun integrationTests(action: Action<TestTypeRule>) = testType(TestType.INTEGRATION, action)
    fun uiTests(action: Action<TestTypeRule>) = testType(TestType.UI, action)
    fun e2eTests(action: Action<TestTypeRule>) = testType(TestType.E2E, action)
    fun apiTests(action: Action<TestTypeRule>) = testType(TestType.API, action)

    /**
     * Get runAllTestsOnCriticalChanges value
     */
    internal val runAllTestsOnCriticalChanges: Boolean
        get() = runAllTestsOnCriticalChangesProperty.get()

    /**
     * Get runUnitTestsByDefault value
     */
    internal val runUnitTestsByDefault: Boolean
        get() = runUnitTestsByDefaultProperty.get()
}

/**
 * Rule for determining when to run specific test types
 */
class TestTypeRule {
    /**
     * File path patterns that trigger these tests when changed
     */
    val pathPatterns = mutableListOf<String>()

    /**
     * Run tests only in changed modules (false = in all dependent modules)
     */
    var runOnlyInChangedModules: Boolean = false

    /**
     * Add path pattern
     */
    fun whenChanged(pattern: String) {
        pathPatterns.add(pattern)
    }

    /**
     * Add multiple path patterns
     */
    fun whenChanged(vararg patterns: String) {
        pathPatterns.addAll(patterns)
    }

    /**
     * Check if file matches the rule
     */
    fun shouldRunForFile(filePath: String): Boolean {
        if (pathPatterns.isEmpty()) {
            return true // If no patterns, applies to all files
        }

        // Normalize file path for comparison (always use /)
        val normalizedFilePath = filePath.replace("\\", "/")

        return pathPatterns.any { pattern ->
            // Normalize pattern too
            val normalizedPattern = pattern.replace("\\", "/")

            when {
                // Pattern like **/something/** - look for /something/ anywhere in path
                normalizedPattern.startsWith("**/") && normalizedPattern.endsWith("/**") -> {
                    val middle = normalizedPattern.removePrefix("**/").removeSuffix("/**")
                    normalizedFilePath.contains("/$middle/")
                }

                normalizedPattern.startsWith("**/") -> {
                    val suffix = normalizedPattern.removePrefix("**/")
                    // **/*.kt - check ending of entire path
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
                    // If pattern contains / - it's a path pattern
                    // If no / - it's a filename pattern
                    if (normalizedPattern.contains("/")) {
                        // Path pattern - convert to regex
                        val regexPattern = normalizedPattern
                            .replace(".", "\\.")
                            .replace("**", "DOUBLE_STAR_PLACEHOLDER")
                            .replace("*", "[^/]*")
                            .replace("DOUBLE_STAR_PLACEHOLDER", ".*")
                        normalizedFilePath.matches(regexPattern.toRegex())
                    } else {
                        // Filename pattern - check only name
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
