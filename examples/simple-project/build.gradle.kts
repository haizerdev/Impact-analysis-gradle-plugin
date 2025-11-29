// Example configuration for simple multi-module project

plugins {
    id("com.impactanalysis.plugin") version "1.0.0"
}

impactAnalysis {
    // Comparing with main branch
    baseBranch.set("origin/main")

    // Include analysis of uncommitted changes
    includeUncommittedChanges.set(true)

    // Unit tests run for all changed modules and their dependencies
    unitTests {
        whenChanged("src/main/**", "src/test/**")
        runOnlyInChangedModules = false
    }

    // Files for linting
    lintFileExtensions.set(listOf("kt", "java"))
}

// Integration with detekt
tasks.register("detektChangedFiles") {
    group = "verification"
    description = "Run detekt only on changed files"

    dependsOn("getChangedFilesForLint")

    doLast {
        val lintFiles = file("build/impact-analysis/lint-files.txt")
        if (lintFiles.exists() && lintFiles.readText().isNotBlank()) {
            exec {
                commandLine(
                    "./gradlew", "detekt",
                    "-Pdetekt.baseline=detekt-baseline.xml"
                )
            }
        } else {
            println("No files to lint")
        }
    }
}
