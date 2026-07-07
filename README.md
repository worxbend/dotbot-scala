<p align="center">
  <img src="docs/logo.svg" alt="dotbot-scala logo" width="720">
</p>

<p align="center">
  <a href="https://github.com/worxbend/dotbot-scala/actions/workflows/ci-native.yml"><img alt="CI" src="https://github.com/worxbend/dotbot-scala/actions/workflows/ci-native.yml/badge.svg"></a>
  <a href="https://github.com/worxbend/dotbot-scala/releases"><img alt="Release" src="https://img.shields.io/github/v/release/worxbend/dotbot-scala?sort=semver"></a>
  <img alt="Scala" src="https://img.shields.io/badge/Scala-3.8.4-dc322f">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-4c566a">
</p>

# dotbot-scala

`dotbot-scala` is a Scala 3 implementation of the core [Dotbot](https://github.com/anishathalye/dotbot) workflow for bootstrapping dotfiles. It focuses on typed parsing, predictable planning, native binaries, and a small CLI surface that is easy to automate.

It mirrors the built-in directive workflow supported by `dotbot-go` and intentionally omits Python plugin support.

## Highlights

- Built-in directives: `defaults`, `clean`, `create`, `link`, and `shell`.
- Config formats: YAML, HOCON, JSON, and TOML.
- `plan` mode for reviewable dry planning, including JSON output for automation.
- `validate` mode for CI checks before applying dotfiles.
- Dry-run mode for apply workflows.
- Native Linux release artifacts for `amd64` and `arm64`.
- Ports-and-adapters architecture with focused unit, property, and golden tests.

## Install

Download a native binary from the latest GitHub release:

```bash
curl -L https://github.com/worxbend/dotbot-scala/releases/latest/download/dotbot-linux-amd64.tar.gz | tar -xz
chmod +x dotbot
sudo mv dotbot /usr/local/bin/dotbot
```

For arm64 Linux, use:

```bash
curl -L https://github.com/worxbend/dotbot-scala/releases/latest/download/dotbot-linux-arm64.tar.gz | tar -xz
chmod +x dotbot
sudo mv dotbot /usr/local/bin/dotbot
```

You can also run from source with Mill:

```bash
./mill app.run -c examples/install.conf.yaml --dry-run
```

## Quick Start

Create an `install.conf.yaml` file in your dotfiles repository:

```yaml
- defaults:
    link:
      create: true
      relink: true

- clean:
    - "~"

- create:
    - "~/.local/bin"
    - "~/.vim/undo-history"

- link:
    ~/.vimrc: vimrc
    ~/.tmux.conf: tmux.conf
    ~/.config/nvim:
      path: nvim
      create: true

- shell:
    - [git submodule update --init --recursive, Installing submodules]
```

Preview the work:

```bash
dotbot plan -c install.conf.yaml
```

Apply it:

```bash
dotbot -c install.conf.yaml
```

## Commands

```bash
dotbot -c install.conf.yaml
dotbot -c install.conf.yaml --dry-run
dotbot validate -c install.conf.yaml
dotbot plan -c install.conf.yaml
dotbot plan -c install.conf.yaml --output json
```

Common options:

| Option | Description |
| --- | --- |
| `-c, --config-file CONFIG_FILE` | Add a config file. Can be provided more than once. |
| `-d, --base-directory BASE_DIR` | Run relative paths from `BASE_DIR`. Defaults to the first config file parent. |
| `--only clean,link` | Run only the listed directives. |
| `--except shell` | Skip the listed directives. |
| `-n, --dry-run` | Print intended apply actions without mutating the filesystem. |
| `-x, --exit-on-failure` | Stop after the first failed directive. |
| `-q, --quiet` | Show only warnings and errors. |
| `-v, --verbose` | Show informational logs. Use `-vv` for debug-level output. |
| `--force-color` / `--no-color` | Force or disable ANSI color output. |
| `--emoji` / `--no-emoji` | Force or disable symbol/emoji output. |
| `-h, --help` | Show CLI help. |
| `-V, --version` | Print version information. |

## Configuration

`dotbot-scala` reads Dotbot-style ordered task lists. Paths on the left side of `link` entries are destinations; paths on the right side are sources relative to the base directory.

Supported file extensions:

| Format | Extensions | Notes |
| --- | --- | --- |
| YAML | `.yaml`, `.yml` | Natural Dotbot-style ordered task syntax. |
| HOCON | `.conf`, `.hocon` | Tasks are read from `tasks = [...]`. |
| JSON | `.json` | Top-level ordered task array. |
| TOML | `.toml` | Tasks are read from `tasks` or `task`. |

JSON5 is not supported. `.json5` files are rejected with the normal unsupported-format error.

Examples are available in:

- [examples/install.conf.yaml](examples/install.conf.yaml)
- [examples/install.conf](examples/install.conf)
- [examples/install.json](examples/install.json)
- [examples/install.toml](examples/install.toml)

## Compatibility

This project targets the core Dotbot workflow and POSIX-like systems. Native CI currently builds Linux binaries for:

- `linux-amd64`
- `linux-arm64`

Python plugins are out of scope. If you need plugin execution, use upstream Dotbot.

## Build

Requirements:

- JDK 21
- Mill wrapper from this repository
- GraalVM 21 with `native-image` only when building native binaries

Build a JVM assembly:

```bash
./mill app.assembly
```

Write the assembly to `dist`:

```bash
./mill app.writeAssembly --dest dist/dotbot-scala.jar
```

Build a GraalVM native image:

```bash
native-image --no-fallback -jar dist/dotbot-scala.jar -o dist/dotbot
```

Generate Scala API docs:

```bash
./mill app.doc
```

## Test

Run the full app test suite:

```bash
./mill app.test
```

Run the CI compile/lint/test sequence locally:

```bash
./mill app.compile
./mill core.fix --check
./mill app.test
```

The golden tests in `app/test/src/io/worxbend/dotbot/golden` snapshot CLI output, exit codes, and filesystem state. They are the main behavior-preservation harness for user-facing workflows.

## Architecture

The codebase follows a ports-and-adapters shape:

- `core` contains domain models, directive specs, planning, outcomes, ports, and directive interpreters.
- `infra` contains config parsing, filesystem, shell, and logging adapters.
- `app` contains CLI composition, command orchestration, and output rendering.

Config is treated as a small program. Validation and planning interpret typed directives without applying effects; apply mode interprets the same directives through filesystem and shell ports.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the decision log and remaining design notes.

## Release

Tagged pushes matching `v*` trigger the native release workflow. The workflow:

1. Compiles and tests on the JVM.
2. Builds a JVM assembly.
3. Builds native Linux binaries for `amd64` and `arm64`.
4. Uploads release archives and SHA-256 checksum files to GitHub Releases.

Current version is managed in [build.mill](build.mill) as `Versions.dotbotScala`.

## Contributing

Start with:

```bash
./mill app.test
```

For behavioral changes, add or update focused tests near the changed module. For CLI-visible behavior, prefer a golden test so stdout, stderr, exit code, and filesystem effects remain explicit.

## License

This repository does not currently include a license file. Add one before relying on this project for public redistribution or downstream packaging.
