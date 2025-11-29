// Example configuration for Backend (Spring Boot/Ktor) project

plugins {
    id("com.impactanalysis.plugin") version "1.0.0"
}

impactAnalysis {
    baseBranch.set("origin/main")

    // Critical changes
    criticalPaths.set(
        listOf(
            "build.gradle",
            "application.yml",
            "application.properties",
            "application-*.yml",
            "bootstrap.yml"
        )
    )

    runAllTestsOnCriticalChanges.set(true)

    // Unit tests
    unitTests {
        whenChanged("src/main/**", "src/test/**")
        runOnlyInChangedModules = false
    }

    // Integration tests (with database, cache, etc.)
    integrationTests {
        whenChanged(
            "**/repository/**",
            "**/database/**",
            "**/migration/**",
            "**/dao/**",
            "**/entity/**"
        )
        runOnlyInChangedModules = false
    }

    // API tests (controllers, endpoints)
    apiTests {
        whenChanged(
            "**/controller/**",
            "**/rest/**",
            "**/api/**",
            "**/endpoint/**",
            "**/dto/**"
        )
        runOnlyInChangedModules = true
    }

    // Contract tests (Spring Cloud Contract, Pact)
    testType(com.impactanalysis.model.TestType.CONTRACT) {
        whenChanged(
            "**/api/**",
            "**/contract/**",
            "**/dto/**"
        )
        runOnlyInChangedModules = false
    }

    // Performance tests
    testType(com.impactanalysis.model.TestType.PERFORMANCE) {
        whenChanged(
            "**/service/**",
            "**/repository/**",
            "**/cache/**"
        )
        runOnlyInChangedModules = true
    }

    lintFileExtensions.set(listOf("kt", "java", "yaml", "yml", "properties"))
}

// Backend tasks
tasks.register("testChangedServices") {
    group = "verification"
    description = "Test only changed services and their dependencies"

    dependsOn("runImpactTests")
}

tasks.register("integrationTestImpact") {
    group = "verification"
    description = "Run integration tests for affected modules"

    dependsOn("calculateImpact")

    doLast {
        exec {
            commandLine(
                "./gradlew", "runImpactTests",
                "-PtestTypes=integration,api"
            )
        }
    }
}

// Check database migration changes
tasks.register("checkDatabaseMigrations") {
    group = "verification"
    description = "Check if database migrations changed"

    dependsOn("getChangedFiles")

    doLast {
        val changedFiles = file("build/impact-analysis/changed-files.txt")
        if (changedFiles.exists()) {
            val hasMigrationChanges = changedFiles.readLines()
                .any { it.contains("/migration/") || it.contains("/flyway/") }

            if (hasMigrationChanges) {
                println("⚠️  Database migrations changed - run full integration tests!")
                exec {
                    commandLine("./gradlew", "integrationTest")
                }
            }
        }
    }
}

tasks.register("testAll") {
    description = "Run all tests with coverage"
    group = "verification"
    dependsOn("runImpactTests")
}
