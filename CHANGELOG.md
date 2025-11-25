# Changelog

## [1.0.1] - 2024

### Fixed

- 🐛 **Gradle Task Caching** - fixed caching issue with `calculateImpact` task
  - Added `outputs.upToDateWhen { false }` to `CalculateImpactTask`
  - Added `outputs.upToDateWhen { false }` to `GetChangedFilesTask`
  - Tasks now always re-run and see actual Git changes
  - No more "UP-TO-DATE" issue when files are modified

### Added

- ➕ Methods `getHeadCommitHash()` and `getUncommittedChangesHash()` in `GitClient`
  - Can be used for more fine-grained cache control in the future

### Documentation

- 📖 **CACHE_FIX.md** - detailed description of the problem and solution

## [1.0.0] - 2024

### Created

- ✅ Full-featured Gradle Impact Analysis Plugin
- ✅ Git changes analysis (JGit)
- ✅ Module dependency graph
- ✅ Test scope determination (unit, integration, UI, E2E, API, etc.)
- ✅ Multi-module project support
- ✅ DSL for configuration
- ✅ 5 Gradle tasks
- ✅ 58 unit and integration tests (~85% coverage)
- ✅ Comprehensive documentation (11 MD files)
- ✅ Example configurations (4 project types)

### Fixed

#### v1 (Core code)

- 🐛 `TestScopeCalculator.kt:33` - added `.get()` for `ListProperty`
- 🐛 `RunImpactTestsTask.kt:93-99` - fixed `ExecSpec` syntax (using `spec` parameter)

#### v2 (Test compilation)

- 🐛 `PluginIntegrationTest.kt:74` - explicit generic type `<ImpactAnalysisExtension>`
- 🐛 `TestScopeCalculatorTest.kt:58-64` - added explicit `rule` parameter in lambda

#### v3 (Test logic)

- 🐛 `TestTypeRule.shouldRunForFile()` - fixed glob pattern handling:
  - Path normalization (always use `/`)
  - Proper handling of `*` and `**` in patterns
  - `*` now means "any characters except `/`" (`[^/]*` in regex)
  - `**` means "any characters including `/`" (`.*` in regex)
  - ✨ **Special handling for `**/word/**`** - patterns like `**/repository/**` now search for `/repository/` in path
- 🐛 `DependencyAnalyzer.isConfigFile()` - added check for `.properties` files

### Known Issues

- ⚠️ PowerShell encoding in Windows may cause issues with command output

### How to Run

```powershell
# Build
gradlew build

# Tests
gradlew test

# Tests with reports
gradlew testWithReport
```

### Test Results (last run)

- ✅ 58 tests written
- ✅ All compile successfully
- ✅ Pattern logic fixed
- 🎯 Expected: all 58 tests should pass

### Documentation

- 📖 **FIRST_RUN.md** - quick start guide
- 📖 **README.md** - main documentation
- 📖 **ARCHITECTURE.md** - plugin architecture
- 📖 **QUICK_START.md** - user guide
- 📖 **TEST_GUIDE.md** - testing guide
- 📖 **IMPLEMENTATION_PLAN.md** - implementation plan

