package com.haizerdev.impactanalysis.extension

import com.haizerdev.impactanalysis.extension.TestTypeRule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for TestTypeRule
 */
class TestTypeRuleTest {

    @Test
    fun `test whenChanged with exact path match`() {
        val rule = TestTypeRule()
        rule.whenChanged("src/main/kotlin/Feature.kt")

        assertTrue(rule.shouldRunForFile("src/main/kotlin/Feature.kt"))
        assertFalse(rule.shouldRunForFile("src/main/kotlin/Other.kt"))
    }

    @Test
    fun `test whenChanged with prefix pattern`() {
        val rule = TestTypeRule()
        rule.whenChanged("src/main/**")

        assertTrue(rule.shouldRunForFile("src/main/kotlin/Feature.kt"))
        assertTrue(rule.shouldRunForFile("src/main/java/App.java"))
        assertTrue(rule.shouldRunForFile("src/main/resources/config.xml"))
        assertFalse(rule.shouldRunForFile("src/test/kotlin/Test.kt"))
    }

    @Test
    fun `test whenChanged with suffix pattern`() {
        val rule = TestTypeRule()
        rule.whenChanged("**/repository/**")

        assertTrue(rule.shouldRunForFile("app/src/main/kotlin/repository/UserRepository.kt"))
        assertTrue(rule.shouldRunForFile("feature/src/main/repository/DataRepository.kt"))
        assertFalse(rule.shouldRunForFile("app/src/main/kotlin/service/UserService.kt"))
    }

    @Test
    fun `test whenChanged with wildcard pattern`() {
        val rule = TestTypeRule()
        rule.whenChanged("**/*.kt")

        assertTrue(rule.shouldRunForFile("app/src/main/Feature.kt"))
        assertTrue(rule.shouldRunForFile("feature/test/Test.kt"))
        assertFalse(rule.shouldRunForFile("app/src/main/App.java"))
    }

    @Test
    fun `test whenChanged with multiple patterns`() {
        val rule = TestTypeRule()
        rule.whenChanged("**/repository/**", "**/database/**", "**/dao/**")

        assertTrue(rule.shouldRunForFile("app/repository/UserRepository.kt"))
        assertTrue(rule.shouldRunForFile("app/database/AppDatabase.kt"))
        assertTrue(rule.shouldRunForFile("app/dao/UserDao.kt"))
        assertFalse(rule.shouldRunForFile("app/service/UserService.kt"))
    }

    @Test
    fun `test whenChanged with varargs`() {
        val rule = TestTypeRule()
        rule.whenChanged("**/ui/**", "**/compose/**", "**/activity/**")

        assertTrue(rule.shouldRunForFile("app/ui/MainActivity.kt"))
        assertTrue(rule.shouldRunForFile("feature/compose/LoginScreen.kt"))
        assertTrue(rule.shouldRunForFile("app/activity/BaseActivity.kt"))
    }

    @Test
    fun `test shouldRunForFile returns true when no patterns specified`() {
        val rule = TestTypeRule()

        // No patterns - applies to all files
        assertTrue(rule.shouldRunForFile("any/file/path.kt"))
        assertTrue(rule.shouldRunForFile("another/path/file.java"))
    }

    @Test
    fun `test shouldRunForFile with complex path pattern`() {
        val rule = TestTypeRule()
        rule.whenChanged("src/main/kotlin/**")

        assertTrue(rule.shouldRunForFile("src/main/kotlin/com/example/Feature.kt"))
        assertTrue(rule.shouldRunForFile("app/src/main/kotlin/App.kt"))
        assertFalse(rule.shouldRunForFile("src/test/kotlin/Test.kt"))
    }

    @Test
    fun `test shouldRunForFile with file extension pattern`() {
        val rule = TestTypeRule()
        rule.whenChanged("*.kt")

        assertTrue(rule.shouldRunForFile("Feature.kt"))
        assertTrue(rule.shouldRunForFile("app/src/main/App.kt"))
        assertFalse(rule.shouldRunForFile("Feature.java"))
    }

    @Test
    fun `test runOnlyInChangedModules flag`() {
        val rule = TestTypeRule()

        // Default is false
        assertFalse(rule.runOnlyInChangedModules)

        // Can be changed
        rule.runOnlyInChangedModules = true
        assertTrue(rule.runOnlyInChangedModules)
    }
}
