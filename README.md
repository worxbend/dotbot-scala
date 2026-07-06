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
- JSON: `.json`
- JSON5: `.json5`
- TOML: `.toml` with tasks under `tasks` or `task`

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

## Run

```bash
./mill app.run -c examples/install.conf.yaml --dry-run
./mill app.run validate -c examples/install.conf.yaml
./mill app.run plan -c examples/install.conf.yaml --output json
```

## Native CI

`.github/workflows/ci-native.yml` runs JVM compile/tests, builds one assembly, and produces Linux native images for:

- `linux-amd64` on `ubuntu-24.04`
- `linux-arm64` on `ubuntu-24.04-arm`

Tagged pushes matching `v*` publish both archives and checksum files to a GitHub Release.

## Platform Scope

This project targets POSIX-like systems only. The native image pipeline builds Linux binaries for amd64 and arm64.
