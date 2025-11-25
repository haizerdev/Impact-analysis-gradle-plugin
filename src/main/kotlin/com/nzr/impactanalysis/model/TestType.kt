package com.nzr.impactanalysis.model

/**
 * Типы тестов, которые могут быть запущены
 */
enum class TestType(val taskSuffix: String) {
    UNIT("test"),                    // Unit тесты
    INTEGRATION("integrationTest"),  // Интеграционные тесты
    UI("uiTest"),                    // UI тесты
    E2E("e2eTest"),                  // End-to-end тесты
    API("apiTest"),                  // API тесты
    PERFORMANCE("performanceTest"),  // Тесты производительности
    CONTRACT("contractTest"),        // Contract тесты
    ALL("allTests");                 // Все тесты

    companion object {
        fun fromString(type: String): TestType? {
            return values().find { it.name.equals(type, ignoreCase = true) }
        }
    }
}
