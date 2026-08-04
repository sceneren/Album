<!-- .codex-version: v1.0.0 (2026.07.03) -->
# AGENTS.md

This project has completed Codex AI assistance initialization.

## Project

- Name: Album
- Platform: Android application
- Build system: Gradle Kotlin DSL
- Language: Kotlin
- UI: Jetpack Compose Material3 in host module only
- Modules: `:app` host demo and reusable data-only Android Library `:album-api`
- App package, namespace, and application id: `com.github.sceneren.album`
- Library namespace: `com.github.sceneren.album.api`
- NDK/C++: no
- References mode: full mode through `.codex/references/_scan.json`

## Required Workflow

Before changing code:

1. Read `.codex/rules/project_rule.md`.
2. Read `.codex/references/_scan.json`.
3. Read the relevant reference file: `.codex/references/app.md` or `.codex/references/album-api.md`.
4. Inspect the source files being changed.

Do not invent APIs, modules, Gradle aliases, permissions, or resources. The source tree and references are the authority.

## Local Skills

- `.codex/skills/plan_mode/SKILL.md`: use for multi-file or architecture-sensitive work.
- `.codex/skills/code_review/SKILL.md`: use when two or more source/config files change, or before finishing risky changes.
- `.codex/skills/performance_check/SKILL.md`: use for MediaStore, image loading, Compose lazy list/grid, startup, or file-cache changes.
- `.codex/skills/project_initialization/SKILL.md`: use only to reinitialize after major project structure changes.

## Agents

- `.codex/agents/arch-review.md`: architecture and data-flow checklist.
- `.codex/agents/resource-sync.md`: Manifest, permissions, resources, and theme checklist.
- `.codex/agents/proactive-correction.md`: placeholder, drift, and hidden-regression checklist.

## References

- `.codex/references/app.md`: module overview and class responsibilities.
- `.codex/references/album-api.md`: reusable library API, routing, persistence, and MediaStore responsibilities.
- `.codex/references/dependencies.md`: Gradle module and dependency map.
- `.codex/references/conventions.md`: coding, media, permission, and test conventions.

Regenerate scan data after module/dependency/source-layout changes:

```bash
python .codex/scripts/gen_references.py
```

Use diff mode for incremental checks:

```bash
python .codex/scripts/gen_references.py --diff
```

## Verification

- Debug build on Windows: `./gradlew.bat :app:assembleDebug`
- Library build on Windows: `./gradlew.bat :album-api:assembleDebug`
- Unit tests on Windows: `./gradlew.bat :album-api:testDebugUnitTest :app:testDebugUnitTest`
- Instrumented tests with device/emulator: `./gradlew.bat :app:connectedDebugAndroidTest`
