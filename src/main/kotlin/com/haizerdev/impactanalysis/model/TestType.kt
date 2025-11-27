package com.haizerdev.impactanalysis.model

/**
 * Test types that can be run
 */
enum class TestType(val taskSuffix: String) {
    UNIT("test"),                    // Unit tests
    INTEGRATION("integrationTest"),  // Integration tests
    UI("uiTest"),                    // UI tests
    E2E("e2eTest"),                  // End-to-end tests
    API("apiTest"),                  // API tests
    PERFORMANCE("performanceTest"),  // Performance tests
    CONTRACT("contractTest"),        // Contract tests
    ALL("allTests");                 // All tests

    companion object {
        fun fromString(type: String): TestType? {
            return values().find { it.name.equals(type, ignoreCase = true) }
        }
    }
}
