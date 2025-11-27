package com.haizerdev.impactanalysis.model

import com.haizerdev.impactanalysis.model.FileLanguage
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for FileLanguage
 */
class FileLanguageTest {

    @Test
    fun `test fromExtension with kotlin`() {
        assertEquals(FileLanguage.KOTLIN, FileLanguage.fromExtension("kt"))
        assertEquals(FileLanguage.KOTLIN, FileLanguage.fromExtension("kts"))
    }

    @Test
    fun `test fromExtension with java`() {
        assertEquals(FileLanguage.JAVA, FileLanguage.fromExtension("java"))
    }

    @Test
    fun `test fromExtension with xml`() {
        assertEquals(FileLanguage.XML, FileLanguage.fromExtension("xml"))
    }

    @Test
    fun `test fromExtension with json`() {
        assertEquals(FileLanguage.JSON, FileLanguage.fromExtension("json"))
    }

    @Test
    fun `test fromExtension with groovy`() {
        assertEquals(FileLanguage.GROOVY, FileLanguage.fromExtension("groovy"))
        assertEquals(FileLanguage.GROOVY, FileLanguage.fromExtension("gradle"))
    }

    @Test
    fun `test fromExtension with properties`() {
        assertEquals(FileLanguage.PROPERTIES, FileLanguage.fromExtension("properties"))
    }

    @Test
    fun `test fromExtension with yaml`() {
        assertEquals(FileLanguage.YAML, FileLanguage.fromExtension("yaml"))
        assertEquals(FileLanguage.YAML, FileLanguage.fromExtension("yml"))
    }

    @Test
    fun `test fromExtension with unknown extension`() {
        assertEquals(FileLanguage.UNKNOWN, FileLanguage.fromExtension("txt"))
        assertEquals(FileLanguage.UNKNOWN, FileLanguage.fromExtension("md"))
        assertEquals(FileLanguage.UNKNOWN, FileLanguage.fromExtension(""))
    }

    @Test
    fun `test fromExtension is case insensitive`() {
        assertEquals(FileLanguage.KOTLIN, FileLanguage.fromExtension("KT"))
        assertEquals(FileLanguage.KOTLIN, FileLanguage.fromExtension("Kt"))
        assertEquals(FileLanguage.JAVA, FileLanguage.fromExtension("JAVA"))
    }

    @Test
    fun `test fromPath with kotlin file`() {
        assertEquals(FileLanguage.KOTLIN, FileLanguage.fromPath("src/main/kotlin/Feature.kt"))
        assertEquals(FileLanguage.KOTLIN, FileLanguage.fromPath("build.gradle.kts"))
    }

    @Test
    fun `test fromPath with java file`() {
        assertEquals(FileLanguage.JAVA, FileLanguage.fromPath("src/main/java/App.java"))
    }

    @Test
    fun `test fromPath with xml file`() {
        assertEquals(FileLanguage.XML, FileLanguage.fromPath("res/layout/activity_main.xml"))
    }

    @Test
    fun `test fromPath with no extension`() {
        assertEquals(FileLanguage.UNKNOWN, FileLanguage.fromPath("README"))
        assertEquals(FileLanguage.UNKNOWN, FileLanguage.fromPath("Dockerfile"))
    }

    @Test
    fun `test fromPath with complex path`() {
        assertEquals(
            FileLanguage.KOTLIN,
            FileLanguage.fromPath("app/src/main/kotlin/com/example/feature/Feature.kt")
        )
    }
}
