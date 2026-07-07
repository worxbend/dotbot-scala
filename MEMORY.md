# Memory

- Learned: centralizing handler registration (via `DirectiveRegistry`) keeps directive onboarding localized and avoids duplicated wiring edits across app and core layers.
- Learned: `Outcome`-based execution with shared `BatchedDirectiveHandler` dramatically reduces repeated fold/summary behavior and preserves soft-failure semantics.
- Learned: normalizing `Either`/`ShellExit` error handling at `RuntimeContext` gives handlers a single error-reporting pattern and cleaner behavior under failure conditions.
