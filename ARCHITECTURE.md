# Impact Analysis Plugin Architecture

## 📋 Implementation Overview

### Project Goals

1. Create a universal Gradle plugin for Git changes analysis
2. Automatically determine scope of different test types
3. Provide list of changed files for linters/detekt
4. Work independently of project configuration
5. Support multi-module projects of any complexity

## 🏗️ Architectural Components

### 1. Git Layer

**Components:**

- `GitClient` - main client for Git operations via JGit
- `GitDiffEntry` - file change model

**Responsibilities:**

- Get list of changed files between commits
- Work with uncommitted changes
- Compare branches
- Determine change type (ADD, MODIFY, DELETE, etc.)

**API:**

```kotlin
class GitClient(projectDir: File) {
    fun getChangedFiles(baseRef: String, compareRef: String): List<GitDiffEntry>
    fun getUncommittedChanges(): List<GitDiffEntry>
    fun getChangedFilesSinceBranch(branchName: String): List<GitDiffEntry>
    fun getCurrentBranch(): String
}
```

### 2. Dependency Analysis Layer

**Components:**

- `ModuleDependencyGraph` - module dependency graph
- `DependencyAnalyzer` - analyzer for module determination

**Responsibilities:**

- Build dependency graph between modules
- Determine which module a file belongs to
- Calculate all affected modules (including transitive dependencies)
- Cache results

**Graph Building Algorithm:**

1. Traverse all projects in `rootProject.allprojects`
2. For each project, analyze all configurations
3. Extract `ProjectDependency` (dependencies between modules)
4. Build bidirectional graph:
    - Direct dependencies: module → dependency
    - Reverse dependencies: dependency → dependents

**API:**

```kotlin
class ModuleDependencyGraph(rootProject: Project) {
    fun getAffectedModules(changedModules: Set<String>): Set<String>
    fun getDirectDependencies(modulePath: String): Set<String>
    fun getDirectDependents(modulePath: String): Set<String>
    fun toDotFormat(): String // For visualization
}

class DependencyAnalyzer(rootProject: Project) {
    fun getModuleForFile(filePath: String): String?
    fun isTestFile(filePath: String): Boolean
    fun isConfigFile(filePath: String): Boolean
}
```

### 3. Test Scope Calculation Layer

**Components:**

- `TestScopeCalculator` - test scope calculator
- `TestType` - enum of test types
- `TestTypeRule` - rules for test types

**Responsibilities:**

- Determine which tests need to run
- Apply user-defined rules
- Handle critical changes
- Generate list of tasks to run

**Algorithm:**

1. Get list of changed files
2. Determine directly affected modules
3. Calculate all dependent modules via graph
4. Check for critical changes
5. Apply rules for each test type
6. Generate list of Gradle tasks

**API:**

```kotlin
class TestScopeCalculator(
    rootProject: Project,
    dependencyGraph: ModuleDependencyGraph,
    dependencyAnalyzer: DependencyAnalyzer,
    extension: ImpactAnalysisExtension
) {
    fun calculateTestScope(changedFiles: List<ChangedFile>): Map<TestType, List<String>>
    fun getPriorityModules(changedFiles: List<ChangedFile>): List<String>
}
```

### 4. Configuration Layer

**Components:**

- `ImpactAnalysisExtension` - DSL for configuration
- `TestTypeRule` - rules for test types

**Responsibilities:**

- Provide DSL for plugin configuration
- Configuration validation
- Default values

**DSL Example:**

```kotlin
impactAnalysis {
    baseBranch.set("origin/main")
    
    unitTests {
        whenChanged("src/main/**")
        runOnlyInChangedModules = false
    }
}
```

### 5. Tasks Layer

**Components:**

- `CalculateImpactTask` - calculate impact analysis
- `GetChangedFilesTask` - get list of files
- `RunImpactTestsTask` - run tests

**Responsibilities:**

- Execute operations
- Save results
- Logging
- Gradle integration

## 🔄 Workflow

### Complete Analysis Cycle:

```
1. User runs: ./gradlew calculateImpact
                   ↓
2. CalculateImpactTask
   - Initializes GitClient
   - Gets list of changed files
                   ↓
3. DependencyAnalyzer
   - Determines module for each file
   - Creates ChangedFile objects
                   ↓
4. ModuleDependencyGraph
   - Builds dependency graph
   - Calculates all affected modules
                   ↓
5. TestScopeCalculator
   - Applies rules from configuration
   - Determines test types to run
   - Generates list of Gradle tasks
                   ↓
6. Save Results
   - JSON file with complete report
   - File lists for linting
                   ↓
7. [Optional] RunImpactTestsTask
   - Reads results
   - Runs necessary tests
```

## 📊 Data Structures

### ImpactAnalysisResult

```kotlin
data class ImpactAnalysisResult(
    val changedFiles: List<ChangedFile>,        // All changed files
    val affectedModules: Set<String>,            // All affected modules
    val testsToRun: Map<TestType, List<String>>, // Tests to run
    val filesToLint: List<String>,               // Files for linting
    val timestamp: Long                          // Analysis time
)
```

### ChangedFile

```kotlin
data class ChangedFile(
    val path: String,           // File path
    val module: String?,        // Module (can be null for root)
    val changeType: ChangeType, // Change type
    val language: FileLanguage? // Programming language
)
```

## 🎯 Key Implementation Features

### 1. Project Configuration Independence

**Problem:** Different projects have different module structures and configurations.

**Solution:**

- Dynamic structure analysis via Gradle API
- Don't rely on hardcoded paths
- Use `rootProject.allprojects` for module discovery
- Automatic module determination by file path

### 2. Multi-Module Project Support

**Implementation:**

- Dependency graph built for all modules
- Support for nested modules (`:app:feature:auth`)
- Transitive dependency analysis
- Optimization through caching

### 3. Flexible Configuration

**Capabilities:**

- DSL for configuration via extension
- Path pattern-based rules
- Support for custom test types
- Command-line parameter overrides

### 4. Performance Optimizations

**Strategies:**

- Lazy initialization of dependency graph
- Cache module determination results
- Use JGit instead of exec commands
- Minimize number of Gradle evaluations

## 🔧 Extensibility

### Adding New Test Types

```kotlin
// 1. Add to enum
enum class TestType(val taskSuffix: String) {
    SCREENSHOT("screenshotTest")
}

// 2. Configure
impactAnalysis {
    testType(TestType.SCREENSHOT) {
        whenChanged("**/ui/**")
    }
}
```

### Adding New Analysis Strategies

```kotlin
interface ImpactAnalysisStrategy {
    fun shouldRunTests(changedFiles: List<ChangedFile>): Boolean
    fun getAffectedModules(changedFiles: List<ChangedFile>): Set<String>
}

// Custom strategy
class DatabaseMigrationStrategy : ImpactAnalysisStrategy {
    override fun shouldRunTests(changedFiles: List<ChangedFile>): Boolean {
        return changedFiles.any { it.path.contains("/migration/") }
    }
    // ...
}
```

## 🧪 Testing

### Unit Tests

- Test GitClient with mock repository
- Test graph building algorithm
- Test scope determination rules

### Integration Tests

- Create test multi-module project
- Simulate Git changes
- Verify correct module determination

### E2E Tests

- Complete cycle from Git changes to test execution
- Testing on real projects

## 📈 Metrics and Monitoring

### What Can Be Tracked:

- Number of changed files
- Number of affected modules
- Analysis execution time
- Percentage of tests that didn't need to run
- CI/CD time savings

### Metrics Export:

```kotlin
data class ImpactMetrics(
    val totalFiles: Int,
    val changedFiles: Int,
    val totalModules: Int,
    val affectedModules: Int,
    val totalTests: Int,
    val testsToRun: Int,
    val savedTime: Duration
)
```

## 🚀 Future Improvements

### Possible Features:

1. **ML-based prediction** - predict problematic changes
2. **Test impact history** - analyze change impact history
3. **Smart parallelization** - optimal test parallelization
4. **Coverage-based analysis** - use code coverage for accuracy
5. **IDE integration** - plugins for IntelliJ IDEA
6. **Web dashboard** - results visualization
7. **Slack/Teams notifications** - result notifications

## 🎨 Best Practices for Usage

### 1. CI/CD Integration

```yaml
# GitHub Actions
- name: Run only affected tests
  run: |
    ./gradlew calculateImpact -PbaseBranch=origin/${{ github.base_ref }}
    ./gradlew runImpactTests
```

### 2. Pre-commit Hook

```bash
#!/bin/bash
./gradlew getChangedFilesForLint
./gradlew detekt --files=$(cat build/impact-analysis/lint-files.txt)
```

### 3. Staged Rollout

```kotlin
// Gradual adoption
impactAnalysis {
    // First, only logging
    runUnitTestsByDefault.set(false)
    
    // Then enable for feature branches
    if (System.getenv("CI_BRANCH") != "main") {
        runUnitTestsByDefault.set(true)
    }
}
```

## 📊 Result Examples

### Time Savings on Typical Project:

**Before using the plugin:**

- All tests: ~45 minutes
- Run on every PR

**After using the plugin:**

- Small PR (1-2 files): ~5 minutes (89% savings)
- Medium PR (5-10 files): ~15 minutes (67% savings)
- Large PR (20+ files): ~30 minutes (33% savings)

### Real Example:

```
Changed files: 3
- feature-auth/LoginViewModel.kt
- feature-auth/LoginViewModelTest.kt
- core-network/ApiClient.kt

Affected modules: 5
- :feature-auth
- :core-network
- :app (depends on feature-auth)
- :feature-profile (depends on core-network)
- :feature-settings (depends on core-network)

Tests to run: 5 tasks instead of 25 tasks
Saved time: 32 minutes
```

## 🔐 Security

### Considerations:

1. **Git credentials** - plugin doesn't require credentials (read-only)
2. **File system access** - only within project
3. **Network access** - not required (all local)
4. **Sensitive data** - results may contain file paths

### Recommendations:

- Don't commit `build/impact-analysis/` to Git
- Add to `.gitignore`
- In CI/CD save results as artifacts

---
