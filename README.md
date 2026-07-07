# dotbot-scala

`dotbot-scala` is a Scala 3 implementation of the core Dotbot workflow. It mirrors `dotbot-go`'s supported built-in directives and intentionally omits Python plugin support.

Supported directives:

- `defaults`
- `clean`
- `create`
- `link`
- `shell`

Supported config formats:

- YAML: `.yaml`, `.yml`
- HOCON: `.conf`, `.hocon`
- JSON: `.json`
- TOML: `.toml` with tasks under `tasks` or `task`

JSON5 is unsupported. `.json5` files are rejected with the normal unsupported-format error.

## Build

```bash
./mill app.assembly
```

Write the assembly to `dist`:

```bash
./mill app.writeAssembly --dest dist/dotbot-scala.jar
```

Build a GraalVM native image when `native-image` is available:

```bash
native-image --no-fallback -jar dist/dotbot-scala.jar -o dist/dotbot
```

## Scala API Docs

Generate Scaladoc output with:

```bash
./mill app.doc
```

You can also generate docs directly with `scaladoc` via Mill if you prefer a custom output directory; see `build.mill` task wiring for the `app.doc` target.

## Run

```bash
./mill app.run -c examples/install.conf.yaml --dry-run
./mill app.run validate -c examples/install.conf.yaml
./mill app.run plan -c examples/install.conf.yaml --output json
```

## Golden Tests

The `app/test/src/io/worxbend/dotbot/golden` suite is the behavior-preservation harness for refactors. It runs sandboxed CLI/app scenarios and snapshots exit code, stdout/stderr, and filesystem state. Run it with the normal test command:

```bash
./mill app.test
```

## Architecture

The codebase is being refactored toward a ports-and-adapters shape:

- `core` holds domain model types, directive specs, planning, outcomes, and directive interpreters.
- `infra` holds config parsing plus filesystem, shell, and logging adapters.
- `app` holds the CLI entry point and composition root.

Config is treated as a small program: validation and planning interpret the typed directives without applying effects, while apply mode interprets the same directives through filesystem and shell ports.

The Mill build enforces this as staged `core`, `infra`, and `app` modules while source files remain under the existing package tree. See `ARCHITECTURE.md` for the decision log and remaining work.

## Native CI

`.github/workflows/ci-native.yml` runs JVM compile/tests, builds one assembly, and produces Linux native images for:

- `linux-amd64` on `ubuntu-24.04`
- `linux-arm64` on `ubuntu-24.04-arm`

Tagged pushes matching `v*` publish both archives and checksum files to a GitHub Release.

## Platform Scope

This project targets POSIX-like systems only. The native image pipeline builds Linux binaries for amd64 and arm64.
