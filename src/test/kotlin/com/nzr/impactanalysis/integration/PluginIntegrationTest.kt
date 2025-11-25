package com.nzr.impactanalysis.integration

import com.nzr.impactanalysis.ImpactAnalysisPlugin
import com.nzr.impactanalysis.extension.ImpactAnalysisExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration тесты для плагина
 */
class PluginIntegrationTest {

    private lateinit var rootProject: Project
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDir("plugin-test")
        rootProject = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .withName("test-project")
            .build()
    }

    @Test
    fun `test plugin can be applied`() {
        rootProject.pluginManager.apply(ImpactAnalysisPlugin::class.java)

        // Проверяем что плагин применился
        assertTrue(rootProject.plugins.hasPlugin(ImpactAnalysisPlugin::class.java))
    }

    @Test
    fun `test plugin registers extension`() {
        rootProject.pluginManager.apply(ImpactAnalysisPlugin::class.java)

        // Проверяем что extension зарегистрирован
        val extension = rootProject.extensions.findByName("impactAnalysis")
        assertNotNull(extension)
    }

    @Test
    fun `test plugin registers tasks`() {
        rootProject.pluginManager.apply(ImpactAnalysisPlugin::class.java)

        // Проверяем что все задачи зарегистрированы
        assertNotNull(rootProject.tasks.findByName("calculateImpact"))
        assertNotNull(rootProject.tasks.findByName("getChangedFiles"))
        assertNotNull(rootProject.tasks.findByName("getChangedFilesForLint"))
        assertNotNull(rootProject.tasks.findByName("runImpactTests"))
        assertNotNull(rootProject.tasks.findByName("impactTest"))
    }

    @Test
    fun `test tasks are in correct group`() {
        rootProject.pluginManager.apply(ImpactAnalysisPlugin::class.java)

        val calculateImpact = rootProject.tasks.findByName("calculateImpact")!!
        assertTrue(calculateImpact.group == "impact analysis")

        val getChangedFiles = rootProject.tasks.findByName("getChangedFiles")!!
        assertTrue(getChangedFiles.group == "impact analysis")
    }

    @Test
    fun `test extension can be configured`() {
        rootProject.pluginManager.apply(ImpactAnalysisPlugin::class.java)

        // Настраиваем extension
        rootProject.extensions.configure<ImpactAnalysisExtension>("impactAnalysis") { extension ->
            extension.baseBranch.set("origin/develop")
            extension.includeUncommittedChanges.set(false)
        }

        // Проверяем что конфигурация применилась
        val extension = rootProject.extensions.getByName("impactAnalysis")
                as ImpactAnalysisExtension

        assertTrue(extension.baseBranch.get() == "origin/develop")
        assertTrue(extension.includeUncommittedChanges.get() == false)
    }
}
