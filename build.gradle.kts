plugins {
    kotlin("jvm") version "1.9.22"
    `java-gradle-plugin`
    `maven-publish`
    jacoco
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "com.nzr.impactanalysis"
version = "1.0.5"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())

    // JGit для работы с Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")

    // Для работы с JSON
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
            id = "com.nzr.impactanalysis"
            implementationClass = "com.nzr.impactanalysis.ImpactAnalysisPlugin"
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

    // Показывать результаты тестов в консоли
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }

    // Параллельное выполнение
    maxParallelForks = Runtime.getRuntime().availableProcessors()

    // Увеличенная память
    maxHeapSize = "2g"

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

    // Минимальный coverage
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
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

// Задача для запуска тестов с отчетом
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
