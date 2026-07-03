# Conflict Resolution

Use this order when instructions disagree:

1. User's latest explicit request.
2. Safety, platform, Android storage, and permission constraints.
3. `.codex/rules/project_rule.md`.
4. Relevant `.codex/references/*.md` module documentation.
5. Existing local code style and naming.
6. General Android or Kotlin best practices.

## Resolution Rules

- Do not satisfy a style preference by weakening runtime behavior, permission correctness, or scoped-storage compatibility.
- If a requested change conflicts with Android platform policy, explain the conflict and implement the nearest compliant behavior.
- If documentation and source disagree, trust source code first, then update references if the task includes initialization or documentation maintenance.
- If a new abstraction is optional, keep the change local unless it removes real duplication or matches an existing package boundary.
- If a change touches two or more source/config files, run the local `code_review` skill before finishing.

