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

It implements the same built-in directive workflow as [`dotbot-go`](https://github.com/worxbend/dotbot-go) and intentionally omits Python plugin support. The two are close but not identical — see [Differences from dotbot-go](#differences-from-dotbot-go).

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
| `--only clean,link` | Run only the listed directives. Unknown names are an error. |
| `--except shell` | Skip the listed directives. Unknown names are an error. |
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

Task order is significant in every supported format: a `defaults` block applies only to the directives written after it. Write one directive per task entry — the shape every example below uses — and the order you write is the order that runs.

HOCON records only the line a value came from, so if a single task entry puts several directives on one line their order cannot be recovered. Rather than guess, `dotbot-scala` reports it and asks you to split them across lines. YAML, JSON and TOML have no such limitation.

Paths may contain `~` and environment variables (`$NAME` or `${NAME}`). A variable that is not set is left in the path as written, so the path fails visibly rather than silently resolving somewhere unintended.

File modes are octal, whether quoted or not: `mode: 0755` and `mode: "0755"` mean the same thing. A decimal value such as `mode: 493` is rejected.

Informational output goes to stdout; warnings and errors go to stderr. This is what makes `dotbot plan --output json > plan.json` safe to redirect.

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

## Differences from dotbot-go

`dotbot-scala` and [`dotbot-go`](https://github.com/worxbend/dotbot-go) implement the same five
directives (`defaults`, `clean`, `create`, `link`, `shell`), the same `apply` / `validate` / `plan`
modes, and the same core options. A configuration that uses one directive per task entry, quoted
file modes, and no exotic flags behaves identically under both. The differences below are the ones
worth knowing before moving a configuration between them.

### Configuration formats

| Format | dotbot-go | dotbot-scala |
| --- | --- | --- |
| YAML, JSON, TOML | yes | yes |
| JSON5 | yes | no — `.json5` is rejected |
| HOCON (`.conf`, `.hocon`) | no | yes |

### Command-line options

- `-Q, --super-quiet` exists in `dotbot-go` only.
- `--emoji` / `--no-emoji` and the decorated plan output exist in `dotbot-scala` only.

### Behavior

**File modes written as numbers.** `dotbot-scala` reads a bare number as octal digits, so
`mode: 0755` and `mode: 755` both mean `rwxr-xr-x`, and a value that is not valid octal
(`mode: 800`) is rejected. `dotbot-go` uses the number as its host YAML parser produced it, which
gives the same answer for `0755` but reads `mode: 755` as octal `1363`. A mode written as the
decimal equivalent (`mode: 493`) works in `dotbot-go` and is rejected here — write `0755` instead.

**Unknown directive names.** `dotbot-scala` rejects a name that is not a directive in `--only` or
`--except`. `dotbot-go` compares the raw text, so a typo such as `--only lnik` matches no directive,
skips every action, and still exits `0`.

**Warnings and errors.** `dotbot-scala` writes them to stderr, so `plan --output json` can be
redirected to a file safely. `dotbot-go` writes all output to stdout.

**Shell used by the `shell` directive.** `dotbot-scala` always uses `/bin/sh`. `dotbot-go` uses
`$SHELL` and falls back to `/bin/sh`, so a directive written in POSIX shell can fail under a
non-POSIX login shell such as fish or tcsh.

**Environment variables that are not set.** `dotbot-scala` leaves `$MISSING` in the path exactly as
written, matching upstream Dotbot's Python behavior, so the path fails visibly instead of quietly
resolving somewhere else. `dotbot-go` substitutes an empty string, which turns
`$XDG_CONFIG_HOME/nvim` into `/nvim` when the variable is unset.

**Option checking in `validate`.** `dotbot-scala` checks each directive's options against the set
that directive accepts, so a mistyped value (`force: yes`, which YAML reads as a string) or a
misspelled key (`relnk`) is reported by name. `dotbot-go` has no equivalent check.

**Directive order in HOCON.** HOCON is `dotbot-scala` only, and it records just the line a value came
from. Several directives written on one line inside a single task therefore cannot be ordered, and
that is reported as an error rather than guessed at. Write one directive per task entry, or one per
line. YAML, JSON and TOML have no such limitation.

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
