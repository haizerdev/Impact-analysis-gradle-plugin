package com.haizerdev.impactanalysis.model

import com.haizerdev.impactanalysis.model.TestType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for TestType enum
 */
class TestTypeTest {

    @Test
    fun `test all test types have task suffix`() {
        assertEquals("test", TestType.UNIT.taskSuffix)
        assertEquals("integrationTest", TestType.INTEGRATION.taskSuffix)
        assertEquals("uiTest", TestType.UI.taskSuffix)
        assertEquals("e2eTest", TestType.E2E.taskSuffix)
        assertEquals("apiTest", TestType.API.taskSuffix)
        assertEquals("performanceTest", TestType.PERFORMANCE.taskSuffix)
        assertEquals("contractTest", TestType.CONTRACT.taskSuffix)
        assertEquals("allTests", TestType.ALL.taskSuffix)
    }

    @Test
    fun `test fromString with valid type names`() {
        assertEquals(TestType.UNIT, TestType.fromString("unit"))
        assertEquals(TestType.UNIT, TestType.fromString("UNIT"))
        assertEquals(TestType.INTEGRATION, TestType.fromString("integration"))
        assertEquals(TestType.UI, TestType.fromString("ui"))
        assertEquals(TestType.E2E, TestType.fromString("e2e"))
        assertEquals(TestType.API, TestType.fromString("api"))
        assertEquals(TestType.PERFORMANCE, TestType.fromString("performance"))
        assertEquals(TestType.CONTRACT, TestType.fromString("contract"))
        assertEquals(TestType.ALL, TestType.fromString("all"))
    }

    @Test
    fun `test fromString is case insensitive`() {
        assertEquals(TestType.UNIT, TestType.fromString("Unit"))
        assertEquals(TestType.INTEGRATION, TestType.fromString("Integration"))
        assertEquals(TestType.UI, TestType.fromString("Ui"))
        assertEquals(TestType.E2E, TestType.fromString("E2E"))
    }

    @Test
    fun `test fromString with invalid type returns null`() {
        assertNull(TestType.fromString("invalid"))
        assertNull(TestType.fromString(""))
        assertNull(TestType.fromString("unknown"))
    }

    @Test
    fun `test all enum values are accessible`() {
        val allTypes = TestType.values()

        assertEquals(8, allTypes.size)
        assert(allTypes.contains(TestType.UNIT))
        assert(allTypes.contains(TestType.INTEGRATION))
        assert(allTypes.contains(TestType.UI))
        assert(allTypes.contains(TestType.E2E))
        assert(allTypes.contains(TestType.API))
        assert(allTypes.contains(TestType.PERFORMANCE))
        assert(allTypes.contains(TestType.CONTRACT))
        assert(allTypes.contains(TestType.ALL))
    }
}
