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

Compiler warnings fail local and CI builds. Scala and Java both target Java 21.

Format owned source roots, then check without rewriting:

```bash
./mill core.reformat + infra.reformat + app.reformat + app.test.reformat
./mill core.checkFormat + infra.checkFormat + app.checkFormat + app.test.checkFormat
```

## Module ownership

- `core/src`: domain types, decoders, handlers, and runtime interfaces. No third-party dependencies.
- `infra/src`: configuration, filesystem, shell, and terminal adapters. Depends on `core`.
- `app/src`: Picocli configuration, composition, and application output. Depends on `core` and `infra`.
- `app/test/src`: unit, property, integration, and golden tests across those boundaries.

Keep filesystem and subprocess operations behind the ports. Use error values for expected failures, stop dependent mutations after a failure, and test preservation of the original data.

## Packaged behavior and performance

```bash
./mill app.writeAssembly --dest dist/dotbot-scala.jar
python3 scripts/smoke_cli.py --jar dist/dotbot-scala.jar
python3 scripts/benchmark_cli.py --jar dist/dotbot-scala.jar --iterations 5
```

Use `--java /path/to/java` to select Java 21 explicitly, or `--binary dist/dotbot` for a native artifact. Smoke checks run in temporary directories and include restrictive-umask and backup-failure cases. CI runs them against the JVM assembly and both native architectures.

Benchmark without concurrent builds. Each sample starts a new process; one warm-up per scenario is excluded. Timings include JVM startup and use a warm filesystem cache. Keep the JSON output when comparing revisions. These are local diagnostics, not timing assertions in CI.
