package com.haizerdev.impactanalysis.dependency

import com.haizerdev.impactanalysis.dependency.DependencyAnalyzer
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for DependencyAnalyzer
 */
class DependencyAnalyzerTest {

    private lateinit var rootProject: Project
    private lateinit var analyzer: DependencyAnalyzer

    @Before
    fun setup() {
        rootProject = ProjectBuilder.builder().withName("root").build()

        // Create test modules
        val app = ProjectBuilder.builder()
            .withName("app")
            .withParent(rootProject)
            .build()

        val feature = ProjectBuilder.builder()
            .withName("feature-auth")
            .withParent(rootProject)
            .build()

        // Apply plugins
        app.pluginManager.apply("java")
        feature.pluginManager.apply("java")

        // Create directory structure
        File(app.projectDir, "src/main/kotlin").mkdirs()
        File(app.projectDir, "src/test/kotlin").mkdirs()
        File(feature.projectDir, "src/main/kotlin").mkdirs()

        // Create build.gradle files
        File(app.projectDir, "build.gradle").writeText("// app build")
        File(feature.projectDir, "build.gradle").writeText("// feature build")

        analyzer = DependencyAnalyzer(rootProject)
    }

    @Test
    fun `test getModuleForFile with app module file`() {
        val file = File(rootProject.projectDir, "app/src/main/kotlin/MainActivity.kt")
        file.parentFile.mkdirs()
        file.writeText("class MainActivity")

        val module = analyzer.getModuleForFile("app/src/main/kotlin/MainActivity.kt")

        assertEquals(":app", module)
    }

    @Test
    fun `test getModuleForFile with feature module file`() {
        val file = File(rootProject.projectDir, "feature-auth/src/main/kotlin/LoginScreen.kt")
        file.parentFile.mkdirs()
        file.writeText("class LoginScreen")

        val module = analyzer.getModuleForFile("feature-auth/src/main/kotlin/LoginScreen.kt")

        assertEquals(":feature-auth", module)
    }

    @Test
    fun `test isTestFile recognizes test files by path`() {
        assertTrue(analyzer.isTestFile("src/test/kotlin/MyTest.kt"))
        assertTrue(analyzer.isTestFile("app/src/test/java/AppTest.java"))
        assertTrue(analyzer.isTestFile("feature/src/androidTest/kotlin/UITest.kt"))
    }

    @Test
    fun `test isTestFile recognizes test files by name`() {
        assertTrue(analyzer.isTestFile("src/main/kotlin/MyTest.kt"))
        assertTrue(analyzer.isTestFile("src/main/kotlin/MyTests.kt"))
        assertTrue(analyzer.isTestFile("src/main/kotlin/MySpec.kt"))
        assertTrue(analyzer.isTestFile("app/MyTest.java"))
    }

    @Test
    fun `test isTestFile returns false for non-test files`() {
        assertFalse(analyzer.isTestFile("src/main/kotlin/MainActivity.kt"))
        assertFalse(analyzer.isTestFile("app/src/main/java/App.java"))
        assertFalse(analyzer.isTestFile("feature/LoginScreen.kt"))
    }

    @Test
    fun `test isConfigFile recognizes gradle files`() {
        assertTrue(analyzer.isConfigFile("build.gradle"))
        assertTrue(analyzer.isConfigFile("app/build.gradle.kts"))
        assertTrue(analyzer.isConfigFile("settings.gradle"))
        assertTrue(analyzer.isConfigFile("settings.gradle.kts"))
    }

    @Test
    fun `test isConfigFile recognizes property files`() {
        assertTrue(analyzer.isConfigFile("gradle.properties"))
        assertTrue(analyzer.isConfigFile("local.properties"))
    }

    @Test
    fun `test isConfigFile recognizes proguard files`() {
        assertTrue(analyzer.isConfigFile("app/proguard-rules.pro"))
        assertTrue(analyzer.isConfigFile("proguard.pro"))
    }

    @Test
    fun `test isConfigFile recognizes gradle directory files`() {
        assertTrue(analyzer.isConfigFile("gradle/wrapper/gradle-wrapper.properties"))
        assertTrue(analyzer.isConfigFile("gradle/libs.versions.toml"))
    }

    @Test
    fun `test isConfigFile returns false for regular files`() {
        assertFalse(analyzer.isConfigFile("src/main/kotlin/MainActivity.kt"))
        assertFalse(analyzer.isConfigFile("README.md"))
        assertFalse(analyzer.isConfigFile("app/src/main/AndroidManifest.xml"))
    }
}
