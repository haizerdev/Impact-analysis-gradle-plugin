// Example configuration for Microservices project
// Structure:
// - services/user-service
// - services/order-service
// - services/payment-service
// - libs/common
// - libs/api-contracts

plugins {
    id("com.impactanalysis.plugin") version "1.0.0"
}

impactAnalysis {
    baseBranch.set("origin/main")

    // Critical changes in shared libraries
    criticalPaths.set(
        listOf(
            "libs/common/**",
            "libs/api-contracts/**",
            "gradle.properties",
            "docker-compose.yml"
        )
    )

    runAllTestsOnCriticalChanges.set(true)

    // Unit tests for each service
    unitTests {
        whenChanged("src/main/**")
        runOnlyInChangedModules = false
    }

    // Integration tests (with DB, cache, message broker)
    integrationTests {
        whenChanged(
            "**/repository/**",
            "**/messaging/**",
            "**/kafka/**",
            "**/rabbitmq/**"
        )
        runOnlyInChangedModules = true
    }

    // API Contract tests (between services)
    testType(com.impactanalysis.model.TestType.CONTRACT) {
        whenChanged(
            "**/api/**",
            "**/contract/**",
            "**/client/**",
            "libs/api-contracts/**"
        )
        runOnlyInChangedModules = false
    }

    // E2E tests (between multiple services)
    e2eTests {
        whenChanged("services/**")
        runOnlyInChangedModules = false
    }

    lintFileExtensions.set(listOf("kt", "java", "yaml", "json"))
}

// Determine which services have changed
tasks.register("detectChangedServices") {
    group = "impact analysis"
    description = "Detect which microservices have changes"

    dependsOn("calculateImpact")

    doLast {
        val resultFile = file("build/impact-analysis/result.json")
        if (resultFile.exists()) {
            val result = com.google.gson.Gson().fromJson(
                resultFile.readText(),
                com.impactanalysis.model.ImpactAnalysisResult::class.java
            )

            val changedServices = result.affectedModules
                .filter { it.contains(":services:") }
                .map { it.substringAfterLast(":") }

            println("\n" + "=".repeat(60))
            println("Changed Microservices:")
            println("=".repeat(60))

            if (changedServices.isEmpty()) {
                println("No services changed")
            } else {
                changedServices.forEach { service ->
                    println("  $service")
                }

                // Save list for Continuous Integration/Continuous Deployment
                file("build/changed-services.txt")
                    .writeText(changedServices.joinToString("\n"))
            }

            println("=".repeat(60))
        }
    }
}

// Run tests only for changed microservices
tasks.register("testChangedMicroservices") {
    group = "verification"
    description = "Test only changed microservices"

    dependsOn("detectChangedServices")

    doLast {
        val servicesFile = file("build/changed-services.txt")
        if (servicesFile.exists()) {
            val services = servicesFile.readLines()
            services.forEach { service ->
                println("Testing service: $service")
                exec {
                    commandLine("./gradlew", ":services:$service:test")
                }
            }
        }
    }
}

// Build Docker images only for changed services
tasks.register("buildChangedServiceImages") {
    group = "build"
    description = "Build Docker images only for changed services"

    dependsOn("detectChangedServices")

    doLast {
        val servicesFile = file("build/changed-services.txt")
        if (servicesFile.exists()) {
            val services = servicesFile.readLines()
            services.forEach { service ->
                println("Building Docker image for: $service")
                exec {
                    commandLine(
                        "./gradlew",
                        ":services:$service:dockerBuild"
                    )
                }
            }
        }
    }
}

// Contract tests between services
tasks.register("verifyServiceContracts") {
    group = "verification"
    description = "Verify API contracts between services"

    dependsOn("calculateImpact")

    doLast {
        val resultFile = file("build/impact-analysis/result.json")
        if (resultFile.exists()) {
            val result = com.google.gson.Gson().fromJson(
                resultFile.readText(),
                com.impactanalysis.model.ImpactAnalysisResult::class.java
            )

            // If contract or API client files changed
            val hasContractChanges = result.changedFiles.any {
                it.path.contains("/api/") ||
                        it.path.contains("/contract/") ||
                        it.path.contains("/client/")
            }

            if (hasContractChanges) {
                println("API contracts changed - running contract tests")
                exec {
                    commandLine(
                        "./gradlew", "runImpactTests",
                        "-PtestTypes=contract"
                    )
                }
            }
        }
    }
}
