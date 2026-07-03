# Proactive Correction Agent

Use this agent to find issues that are easy to miss after implementation.

## Rule Integrity

- [ ] `.codex/rules/project_rule.md` has no placeholder text.
- [ ] `.codex/references/_scan.json` matches `settings.gradle.kts`.
- [ ] Every module in `_scan.json` has a corresponding `.codex/references/{module}.md`.
- [ ] AGENTS.md points to initialized rules and references.

## Code Health

- [ ] No MediaStore or file-copy work was added on the main thread.
- [ ] No new deprecated raw external file path usage was added.
- [ ] No new permission prompt can repeat on ordinary recomposition.
- [ ] No load-more loop can trigger after `hasMoreData` becomes false.
- [ ] New public reusable APIs have concise KDoc.

## Documentation Drift

- [ ] If modules, dependencies, SDK values, or package names changed, rerun `python .codex/scripts/gen_references.py`.
- [ ] If source responsibilities changed, update `.codex/references/app.md`, `.codex/references/dependencies.md`, and `.codex/references/conventions.md`.
- [ ] If two or more source/config files changed, run the `code_review` skill.

