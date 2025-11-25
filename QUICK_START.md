# Quick Start Guide - Impact Analysis Plugin

## 🚀 Quick Start in 5 Minutes

### Step 1: Installation

Add the plugin to your root `build.gradle.kts`:

```kotlin
plugins {
    id("com.nzr.impact-analysis") version "1.0.1"
}
```

### Step 2: Minimal Configuration

```kotlin
impactAnalysis {
    baseBranch.set("origin/main")  // Your base branch
}
```

### Step 3: First Run

```bash
# Analyze changes
./gradlew calculateImpact

# View result
cat build/impact-analysis/result.json
```

**Done!** 🎉 The plugin will now analyze changes and show which tests need to run.

---

## 📋 Basic Commands

### 1. Analyze Changes

```bash
./gradlew calculateImpact
```

**Result:** `build/impact-analysis/result.json` contains:

- List of changed files
- Affected modules
- Tests to run
- Files for linting

### 2. Get List of Changed Files

```bash
./gradlew getChangedFiles
```

**Result:** `build/impact-analysis/changed-files.txt`

### 3. Get Files for Linting

```bash
./gradlew getChangedFilesForLint
```

**Result:** `build/impact-analysis/lint-files.txt`

### 4. Run Only Necessary Tests

```bash
./gradlew impactTest
```

This automatically:

1. Analyzes changes
2. Determines which tests to run
3. Runs only necessary tests

---

## ⚙️ Typical Configurations

### For Android Project

```kotlin
impactAnalysis {
    baseBranch.set("origin/develop")
    
    // Unit tests
    unitTests {
        whenChanged("src/main/**")
        runOnlyInChangedModules = false
    }
    
    // UI tests
    uiTests {
        whenChanged("**/compose/**", "**/res/layout/**")
        runOnlyInChangedModules = false
    }
    
    // Linting files
    lintFileExtensions.set(listOf("kt", "java", "xml"))
}
```

### For Backend (Spring Boot)

```kotlin
impactAnalysis {
    baseBranch.set("origin/main")
    
    // Unit tests
    unitTests {
        whenChanged("src/main/**")
        runOnlyInChangedModules = false
    }
    
    // Integration tests
    integrationTests {
        whenChanged("**/repository/**", "**/database/**")
        runOnlyInChangedModules = false
    }
    
    // API tests
    apiTests {
        whenChanged("**/controller/**", "**/api/**")
        runOnlyInChangedModules = true
    }
}
```

### For Microservices

```kotlin
impactAnalysis {
    baseBranch.set("origin/main")
    
    // Critical changes in shared libraries
    criticalPaths.set(listOf(
        "libs/common/**",
        "libs/api-contracts/**"
    ))
    
    runAllTestsOnCriticalChanges.set(true)
    
    unitTests {
        whenChanged("src/main/**")
        runOnlyInChangedModules = false
    }
    
    e2eTests {
        whenChanged("services/**")
        runOnlyInChangedModules = false
    }
}
```

---

## 🔧 CI/CD Integration

### GitHub Actions

```yaml
name: Impact Tests

on:
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0  # Important!
      
      - name: Setup JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Run Impact Tests
        run: ./gradlew impactTest -PbaseBranch=origin/${{ github.base_ref }}
```

### GitLab CI

```yaml
impact-test:
  stage: test
  script:
    - git fetch origin $CI_MERGE_REQUEST_TARGET_BRANCH_NAME
    - ./gradlew impactTest -PbaseBranch=origin/$CI_MERGE_REQUEST_TARGET_BRANCH_NAME
```

### Jenkins

```groovy
stage('Impact Tests') {
    steps {
        sh './gradlew impactTest -PbaseBranch=origin/main'
    }
}
```

---

## 🎯 Integration with Detekt/Linter

### Option 1: Direct Integration

```kotlin
tasks.register("detektChanged") {
    dependsOn("getChangedFilesForLint")
    
    doLast {
        val files = file("build/impact-analysis/lint-files.txt").readLines()
        if (files.isNotEmpty()) {
            exec {
                commandLine("./gradlew", "detekt", "-Pdetekt.files=${files.joinToString(",")}")
            }
        }
    }
}
```

### Option 2: In CI/CD

```bash
# Get changed files
./gradlew getChangedFilesForLint

# Run detekt only on them
if [ -s build/impact-analysis/lint-files.txt ]; then
  ./gradlew detekt --input=$(cat build/impact-analysis/lint-files.txt | tr '\n' ',')
fi
```

---

## 📊 Reading Results

### Via Command Line

```bash
# View result
cat build/impact-analysis/result.json | jq '.'

# Only affected modules
cat build/impact-analysis/result.json | jq '.affectedModules'

# Only tests to run
cat build/impact-analysis/result.json | jq '.testsToRun'
```

### Via Gradle Task

```kotlin
tasks.register("showImpact") {
    dependsOn("calculateImpact")
    
    doLast {
        val result = com.google.gson.Gson().fromJson(
            file("build/impact-analysis/result.json").readText(),
            com.nzr.impact_analysis.model.ImpactAnalysisResult::class.java
        )
        
        println("Affected modules: ${result.affectedModules}")
        println("Tests to run: ${result.testsToRun}")
    }
}
```

---

## 💡 Useful Tips

### 1. Compare with Specific Branch

```bash
./gradlew calculateImpact -PbaseBranch=origin/develop
```

### 2. Ignore Uncommitted Changes

```kotlin
impactAnalysis {
    includeUncommittedChanges.set(false)
}
```

### 3. Run Only Unit Tests

```bash
./gradlew runImpactTests -PtestTypes=unit
```

### 4. Run Multiple Test Types

```bash
./gradlew runImpactTests -PtestTypes=unit,integration
```

### 5. Continue on Failure

```bash
./gradlew runImpactTests -PcontinueOnFailure=true
```

---

## 🐛 Troubleshooting

### Problem: "Git repository not found"

**Solution:** Make sure you're in a Git repository and `.git` folder exists

### Problem: "No changes detected"

**Solution:**

- Check that commits exist
- Use `git status` to verify changes
- Verify `baseBranch` is correct

### Problem: "Module not found for file"

**Solution:**

- File might be in project root (not in a module)
- Check that module has a `build.gradle` file

### Problem: Plugin doesn't find tests

**Solution:**

- Make sure test tasks exist (`:test`, `:integrationTest`, etc.)
- Check `whenChanged` rule configuration

---

## 📚 Further Reading

- [README.md](README.md) - Full documentation
- [ARCHITECTURE.md](ARCHITECTURE.md) - Plugin architecture
- [examples/](examples/) - Configuration examples

---

## 🎓 Tutorial Examples

### Example 1: Basic Usage

```bash
# 1. Create commit with changes
git add MyFile.kt
git commit -m "Update MyFile"

# 2. Run analysis
./gradlew calculateImpact

# 3. View result
cat build/impact-analysis/result.json
```

### Example 2: Branch Comparison

```bash
# Compare your feature branch with main
git checkout feature/my-feature
./gradlew calculateImpact -PbaseBranch=origin/main
```

### Example 3: CI/CD Workflow

```bash
# 1. In PR, compare with target branch
./gradlew calculateImpact -PbaseBranch=origin/main

# 2. Run only necessary tests
./gradlew runImpactTests

# 3. Lint only changed files
./gradlew getChangedFilesForLint
./gradlew detekt --input=@build/impact-analysis/lint-files.txt
```

---

## ⚡ Quick Templates

### Minimal Configuration

```kotlin
plugins {
    id("com.nzr.impact-analysis") version "1.0.1"
}

impactAnalysis {
    baseBranch.set("origin/main")
}
```

### Full Configuration

```kotlin
plugins {
    id("com.nzr.impact-analysis") version "1.0.1"
}

impactAnalysis {
    baseBranch.set("origin/main")
    compareBranch.set("HEAD")
    includeUncommittedChanges.set(true)
    runAllTestsOnCriticalChanges.set(true)
    runUnitTestsByDefault.set(true)
    
    criticalPaths.set(listOf(
        "build.gradle",
        "gradle.properties"
    ))
    
    lintFileExtensions.set(listOf("kt", "java"))
    
    unitTests {
        whenChanged("src/main/**")
        runOnlyInChangedModules = false
    }
}
```

---

**Ready to start?** Try the plugin right now! 🚀

```bash
./gradlew calculateImpact
```
