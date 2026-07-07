# Contributing

## Test Tiers

Run the full suite before handing off changes:

```bash
./mill app.test
```

The suite has three tiers:

- Golden integration tests in `app/test/src/io/worxbend/dotbot/golden` preserve observable behavior: exit codes, log text, plan output, and filesystem effects.
- Unit tests cover pure helpers and handler behavior against injected fakes from `app/test/src/io/worxbend/dotbot/testkit`.
- Property tests use `munit-scalacheck` for broad utility invariants such as path relationships, glob detection, and POSIX mode mapping.

Golden output changes are behavior changes. Do not update golden expectations as part of a refactor without explicit review of the changed behavior.

## Linting

Core sources are checked with Scalafix:

```bash
./mill core.fix --check
```

The core rules organize imports and reject `var`, `return`, and `null` in the pure domain layer. Adapter and CLI code may use local mutation or Java interop null checks where the underlying APIs require it.
