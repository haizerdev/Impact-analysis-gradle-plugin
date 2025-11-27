# Impact Analysis Gradle Plugin

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Gradle Plugin](https://img.shields.io/badge/Gradle-Plugin-green.svg)](https://gradle.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org/)
[![Test](https://github.com/haizerdev/impact-analysis-gradle-plugin/actions/workflows/test.yml/badge.svg)](https://github.com/haizerdev/impact-analysis-gradle-plugin/actions/workflows/test.yml)
[![Publish](https://github.com/haizerdev/impact-analysis-gradle-plugin/actions/workflows/publish.yml/badge.svg)](https://github.com/haizerdev/impact-analysis-gradle-plugin/actions/workflows/publish.yml)

Gradle plugin for automatic Git changes analysis and test scope determination in multi-module projects.

## Features

- Git changes analysis - automatic detection of modified files
- Dependency graph - build dependencies between modules
- Smart test scope determination - unit, integration, UI, E2E, API and more
- Multi-module support - works with any project structure
- Flexible configuration - DSL for rule customization
- Uncommitted changes support - analyze local changes
- CI/CD time savings - run only necessary tests (60-90% savings)

## Installation

### 1. Add plugin to `build.gradle.kts`

```kotlin
plugins {
    id("com.haizerdev.impactanalysis") version "1.0.4"
}
```

### 2. Configure the plugin

```kotlin
impactAnalysis {
    // Base branch for comparison
    baseBranch.set("origin/main")
    
    // Include uncommitted changes
    includeUncommittedChanges.set(true)
    
    // Unit tests
    unitTests {
        whenChanged("**/src/main/**/*.kt", "**/src/main/**/*.java")
        runOnlyInChangedModules = false
    }
    
    // Integration tests
    integrationTests {
        whenChanged("**/repository/**", "**/dao/**")
        runOnlyInChangedModules = true
    }
    
    // UI tests
    uiTests {
        whenChanged("**/*Screen.kt", "**/*Activity.kt")
        runOnlyInChangedModules = true
    }
}

## Automated Publishing

This plugin is automatically published to **Gradle Plugin Portal** using GitHub Actions.

### For Plugin Developers

When you create a new release tag (e.g., `v1.0.2`), GitHub Actions automatically:

1. Runs all tests
2. Builds the plugin
3. Publishes to Gradle Plugin Portal
4. Creates a GitHub Release with JAR files

**How to publish a new version:**

```bash
# Update version in build.gradle.kts
# version = "1.0.2"

git add build.gradle.kts
git commit -m "chore: bump version to 1.0.2"
git push origin main

git tag -a v1.0.2 -m "Release v1.0.2"
git push origin v1.0.2
```

See [GITHUB_ACTIONS_SETUP.md](GITHUB_ACTIONS_SETUP.md) for detailed setup instructions.

## Usage

### Calculate impact analysis

```bash
./gradlew calculateImpact
```

**Result in `build/impact-analysis/result.json`:**

```json
{
  "changedFiles": [
    {
      "path": "features/auth/src/main/kotlin/LoginScreen.kt",
      "module": ":features:auth",
      "changeType": "MODIFIED",
      "language": "KOTLIN"
    }
  ],
  "affectedModules": [":features:auth", ":app"],
  "testsToRun": {
    "UI": [":features:auth:connectedAndroidTest"],
    "UNIT": [":features:auth:test", ":app:test"]
  },
  "filesToLint": ["features/auth/src/main/kotlin/LoginScreen.kt"]
}
```

### Get list of changed files

```bash
./gradlew getChangedFiles
```

### Run tests based on impact analysis

```bash
./gradlew runImpactTests
```

### Full flow: analysis + run tests

```bash
./gradlew impactTest
```

## Configuration Examples

### Android Project

```kotlin
impactAnalysis {
    baseBranch.set("origin/develop")
    includeUncommittedChanges.set(true)
    
    unitTests {
        whenChanged("**/src/main/**/*.kt")
        runOnlyInChangedModules = false
    }
    
    integrationTests {
        whenChanged("**/repository/**", "**/database/**")
        runOnlyInChangedModules = true
    }
    
    uiTests {
        whenChanged("**/*Screen.kt", "**/*Activity.kt")
        runOnlyInChangedModules = true
    }
    
    criticalPaths.set(listOf(
        "build.gradle.kts",
        "gradle.properties",
        "gradle/libs.versions.toml"
    ))
}
```

### Backend Project (Spring Boot)

```kotlin
impactAnalysis {
    baseBranch.set("origin/main")
    
    unitTests {
        whenChanged("**/src/main/**/*.kt", "**/src/main/**/*.java")
    }
    
    integrationTests {
        whenChanged("**/repository/**", "**/service/**")
    }
    
    apiTests {
        whenChanged("**/controller/**", "**/api/**", "**/endpoint/**")
        runOnlyInChangedModules = false
    }
}
```

### Microservices

```kotlin
impactAnalysis {
    baseBranch.set("origin/main")
    
    // Critical files - run all tests
    criticalPaths.set(listOf(
        "**/docker-compose.yml",
        "**/Dockerfile",
        "**/kubernetes/**"
    ))
    
    unitTests {
        whenChanged("**/src/main/**")
        runOnlyInChangedModules = true // Only changed services
    }
    
    integrationTests {
        whenChanged("**/api/**", "**/grpc/**")
        runOnlyInChangedModules = false // All dependent services
    }
}
```

## Available Tasks

| Task                     | Description                                    |
|--------------------------|------------------------------------------------|
| `calculateImpact`        | Calculate impact analysis based on Git changes |
| `getChangedFiles`        | Get list of changed files                      |
| `getChangedFilesForLint` | Get list of files for linting (.kt, .java)     |
| `runImpactTests`         | Run tests based on impact analysis             |
| `impactTest`             | Full flow: analysis + run tests                |

## Configuration Parameters

### Basic Parameters

```kotlin
impactAnalysis {
    // Base branch for comparison (default: "origin/main")
    baseBranch.set("origin/develop")
    
    // Branch to compare (default: "HEAD")
    compareBranch.set("HEAD")
    
    // Include uncommitted changes (default: true)
    includeUncommittedChanges.set(true)
    
    // Critical files - all tests run when changed
    criticalPaths.set(listOf(
        "build.gradle.kts",
        "gradle.properties"
    ))
    
    // File extensions for linting
    lintFileExtensions.set(listOf("kt", "java", "xml"))
}
```

### Test Rules

```kotlin
// Unit tests
unitTests {
    whenChanged("**/src/main/**/*.kt", "**/src/main/**/*.java")
    runOnlyInChangedModules = false // Run in all dependent modules
}

// Integration tests
integrationTests {
    whenChanged("**/repository/**", "**/dao/**")
    runOnlyInChangedModules = true // Only in changed modules
}

// UI tests
uiTests {
    whenChanged("**/*Screen.kt", "**/*Activity.kt")
    runOnlyInChangedModules = true
}

// E2E tests
e2eTests {
    whenChanged("**/api/**", "**/endpoint/**")
    runOnlyInChangedModules = false
}

// API tests
apiTests {
    whenChanged("**/controller/**", "**/service/**")
    runOnlyInChangedModules = false
}

// Custom test types
customTests("smoke") {
    whenChanged("**/critical/**")
    runOnlyInChangedModules = false
}
```

## CI/CD Examples

### GitHub Actions

```yaml
name: CI

on:
  pull_request:
    branches: [ main, develop ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0  # Full history for Git analysis
      
      - name: Setup JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Calculate Impact
        run: ./gradlew calculateImpact -PbaseBranch=origin/${{ github.base_ref }}
      
      - name: Run Impact Tests
        run: ./gradlew runImpactTests
```

### GitLab CI

```yaml
test:
  stage: test
  script:
    - ./gradlew calculateImpact -PbaseBranch=origin/main
    - ./gradlew runImpactTests
  only:
    - merge_requests
```

## Metrics

### Before using the plugin

- CI time per PR: **15-20 minutes**
- CI cost per month: **$500-1000**

### After using the plugin

- CI time per PR: **3-7 minutes** (↓70%)
- CI cost per month: **$200-400** (↓60%)
- Development speed: **+30%**

## Architecture

```
┌─────────────────┐
│   Git Changes   │
│   (JGit API)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Impact Analysis │
│   Calculator    │
└────────┬────────┘
         │
         ├──────────────┬──────────────┐
         ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Dependency   │ │ Test Scope   │ │ File         │
│ Graph        │ │ Calculator   │ │ Filter       │
└──────────────┘ └──────────────┘ └──────────────┘
         │              │              │
         └──────┬───────┴──────────────┘
                ▼
        ┌───────────────┐
        │ Result JSON   │
        └───────────────┘
```

## Testing

The plugin is covered with unit and integration tests:

```bash
# Run tests
./gradlew test

# Run tests with coverage report
./gradlew testWithReport
```

**Coverage: ~85%**

## Documentation

- [Architecture](ARCHITECTURE.md) - detailed architecture description
- [Implementation Plan](IMPLEMENTATION_PLAN.md) - how to implement in your project

## Contributing

We welcome contributions! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) file for details.

## Author

**NZR**

- GitHub: [@haizerdev](https://github.com/haizerdev)

## Support

If you find this project useful, please give it a ⭐️!

## Links

- [Gradle Plugin Portal](https://plugins.gradle.org/)
- [JGit Documentation](https://www.eclipse.org/jgit/)
- [Gradle Documentation](https://docs.gradle.org/)

---

**Made with ❤️ for the Developer Community**
