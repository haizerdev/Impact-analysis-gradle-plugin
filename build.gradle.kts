plugins {
    kotlin("jvm") version "1.9.22"
    `java-gradle-plugin`
    `maven-publish`
    jacoco
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "com.haizerdev.impactanalysis"
version = "1.0.14"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())

    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")

    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website.set("https://github.com/haizerdev/impact-analysis-gradle-plugin")
    vcsUrl.set("https://github.com/haizerdev/impact-analysis-gradle-plugin.git")

    plugins {
        create("impactAnalysisPlugin") {
            id = "com.haizerdev.impactanalysis"
            implementationClass = "com.haizerdev.impactanalysis.ImpactAnalysisPlugin"
            displayName = "Impact Analysis Plugin"
            description =
                "Gradle plugin for automatic Git changes analysis and test scope determination in multi-module projects"
            tags.set(listOf("git", "testing", "ci-cd", "analysis", "multi-module", "impact-analysis"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }

    maxParallelForks = Runtime.getRuntime().availableProcessors()

    maxHeapSize = "1g"

    // Coverage
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    doLast {
        val report = file("${buildDir}/reports/jacoco/test/html/index.html")
        if (report.exists()) {
            println("Coverage report: ${report.absolutePath}")
        }
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.65".toBigDecimal()
            }
        }
    }
}

tasks.register("testWithReport") {
    group = "verification"
    description = "Run tests and generate coverage report"

    dependsOn(tasks.test, tasks.jacocoTestReport)

    doLast {
        println("\n✅ Tests completed!")
        println("📊 Test report: ${buildDir}/reports/tests/test/index.html")
        println("📈 Coverage report: ${buildDir}/reports/jacoco/test/html/index.html")
    }
}
