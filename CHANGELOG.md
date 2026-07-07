# Changelog

## internal/refactor

- Added a central directive handler registry (`DirectiveRegistry`) and wired composition root (`Wiring`) to consume it, reducing directive onboarding blast radius.
- Introduced `BatchedDirectiveHandler` to remove duplicate directive execution folding logic and standardized handler outcomes.
- Added `RuntimeContext` shell/filesystem normalization helpers (`withFilesystem`, `runShell`) and migrated handler-side error handling to keep behavior unchanged while simplifying failure paths.
