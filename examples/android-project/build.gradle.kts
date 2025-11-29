// Example configuration for Android multi-module project

plugins {
    id("com.impactanalysis.plugin") version "1.0.0"
}

impactAnalysis {
    baseBranch.set("origin/develop")
    compareBranch.set("HEAD")

    // Critical files - triggers all tests
    criticalPaths.set(
        listOf(
            "build.gradle",
            "build.gradle.kts",
            "gradle.properties",
            "proguard-rules.pro",
            "AndroidManifest.xml"
        )
    )

    // Run unit tests (JVM)
    unitTests {
        isEnable = true
        whenChanged(
            "src/main/java/**",
            "src/main/kotlin/**",
            "src/test/**"
        )
        runOnlyInChangedModules = false
    }

    // Android Instrumentation tests
    integrationTests {
        isEnable = false
        whenChanged(
            "**/ui/**",
            "**/activity/**",
            "**/fragment/**",
            "**/service/**",
            "**/receiver/**"
        )
        runOnlyInChangedModules = true
    }

    // Run UI tests (Compose/Espresso)
    uiTests {
        isEnable = true
        whenChanged(
            "**/compose/**",
            "**/res/layout/**",
            "**/res/values/**",
            "**/ui/screen/**"
        )
        runOnlyInChangedModules = false
    }

    // Screenshot tests
    testType(com.impactanalysis.model.TestType.E2E) {
        isEnable = false
        whenChanged("**/compose/**", "**/ui/**")
        runOnlyInChangedModules = true
    }

    // Files for linting
    lintFileExtensions.set(listOf("kt", "java", "xml"))
}

// Additional tasks for Android
tasks.register("runUnitTestsOnly") {
    group = "verification"
    description = "Run only unit tests based on impact analysis"

    dependsOn("calculateImpact")

    doLast {
        exec {
            commandLine("./gradlew", "runImpactTests", "-PtestTypes=unit")
        }
    }
}

tasks.register("runAndroidTests") {
    group = "verification"
    description = "Run Android instrumentation tests based on impact"

    dependsOn("calculateImpact")

    doLast {
        exec {
            commandLine(
                "./gradlew", "runImpactTests",
                "-PtestTypes=integration,ui"
            )
        }
    }
}

// Lint tasks
tasks.register("lintImpact") {
    group = "verification"
    description = "Run Android Lint on changed modules"

    dependsOn("calculateImpact")

    doLast {
        val resultFile = file("build/impact-analysis/result.json")
        if (resultFile.exists()) {
            val result = com.google.gson.Gson().fromJson(
                resultFile.readText(),
                com.impactanalysis.model.ImpactAnalysisResult::class.java
            )

            result.affectedModules.forEach { module ->
                println("Running lint for $module")
                exec {
                    commandLine("./gradlew", "$module:lint")
                    isIgnoreExitValue = true
                }
            }
        }
    }
}

tasks.register("testWithReport") {
    group = "verification"
    description = "Run tests with report"

    doLast {
        exec {
            commandLine("./gradlew", "test")
        }
        exec {
            commandLine("./gradlew", "jacocoTestReport")
        }
    }
}
