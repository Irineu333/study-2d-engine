## MODIFIED Requirements

### Requirement: Skiko-runtime module is a Compose-free engine boundary

The `:engine-skiko` Gradle module SHALL declare a dependency on Skiko (`org.jetbrains.skiko:skiko-awt` + a platform-classifier runtime artifact such as `skiko-awt-runtime-macos-arm64`) and on `:engine`, and SHALL NOT depend on any `org.jetbrains.compose.*` or `androidx.compose.*` artifact, directly or transitively. The platform-classifier runtime artifact MUST be resolved at build time based on `System.getProperty("os.name")` and `System.getProperty("os.arch")`. The Skiko version MUST be pinned in `gradle/libs.versions.toml` and consumed via the version catalog (the previous version-match constraint against `:engine-compose` no longer applies because that module is removed).

Game modules MAY depend on `:engine-skiko` to obtain the runtime they need; they MUST NOT re-export Skiko types in their own public API.

#### Scenario: Module graph respects the boundary

- **WHEN** `./gradlew :engine-skiko:dependencies` is run
- **THEN** no `org.jetbrains.compose.*` artifact appears in the resolved graph
- **AND** `org.jetbrains.skiko:skiko-awt` appears in the graph
- **AND** a `skiko-awt-runtime-<osArch>` artifact matching the build machine appears in the runtime classpath

#### Scenario: Skiko version comes from the version catalog

- **WHEN** the Skiko version resolved by `:engine-skiko` is inspected
- **THEN** it matches the version declared in `gradle/libs.versions.toml`
- **AND** there is no second `:engine-compose` module in the build whose transitive Skiko version could conflict
