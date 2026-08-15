# Changelog

## unreleased

### Fixed

- **File modes written as numbers are read as octal.** `mode: 0755` was being used as the decimal
  number 755, whose permission bits are `-wxrw--wx` — unreadable by its owner and writable by
  everyone else. It now means what it says, and an impossible mode such as `mode: 800` is rejected
  instead of silently accepted. A mode written as a decimal number (`mode: 493`) is no longer
  accepted; write `0755` or `"0755"`.
- **`--only` and `--except` reject unknown directive names.** A typo such as `--only lnik` used to
  match no directive, so every action was skipped and dotbot still printed
  "All tasks executed successfully" and exited 0.
- **HOCON and JSON configs keep the order they were written in.** Directives inside a task were
  being reordered, so a `defaults` block could end up applying after the directives it was meant
  to configure. The same config written in YAML or TOML was unaffected.
- **Warnings and errors go to stderr.** They previously went to stdout, which meant a warning
  emitted during `plan --output json` was written into the middle of the JSON document and could
  not be filtered out.
- **`shell` directives run under `/bin/sh`.** They previously used `$SHELL`, so a config written in
  POSIX shell failed for anyone whose login shell was fish or tcsh.
- **`stdin: true` works.** A command that reads input used to block on a stream nothing ever wrote
  to, showing no prompt, until it was killed as a timeout ten minutes later.
- **Environment variables expand correctly.** A value containing `$` either crashed the run or
  silently corrupted the path. An unset variable is now left in place rather than replaced with an
  empty string, which used to turn `$XDG_CONFIG_HOME/nvim` into `/nvim` and aim operations at the
  filesystem root.
- **A link whose target is missing no longer creates directories.** With `create: true` the parent
  directory was created before the target was checked, so a failed link still changed the
  filesystem.
- **Recursive `clean` no longer re-expands directory names.** A directory legitimately named
  `$cache` or `~` was being substituted, so the wrong path was inspected.
- **`validate` catches mistyped and misspelled options.** Each directive now declares the options
  it accepts and their types, so `force: yes` (a string in YAML, not a boolean) and a misspelled
  key such as `relnk` are reported by name instead of being silently ignored.
- **Malformed entries say what they were.** A directive entry that could not be understood failed
  without logging anything, leaving only a summary line.
- Log labels, plan column alignment, and the decorated plan box render correctly again.

### Performance

- Glob expansion is bounded to the depth its pattern can match, instead of walking the whole
  subtree once per pattern and once per exclude.

## internal/refactor

- Extracted an `Environment` port so that nothing below the composition root reads `sys.env` or
  `sys.props`; path expansion moved from the `PathUtil` object into an injectable `PathResolver`.
- Replaced `ColorSupport` and `SymbolSupport` with a single injectable `TerminalCapabilities`
  whose detection rules are a pure function of the environment.
- Added a central directive handler registry (`DirectiveRegistry`) and wired composition root (`Wiring`) to consume it, reducing directive onboarding blast radius.
- Introduced `BatchedDirectiveHandler` to remove duplicate directive execution folding logic and standardized handler outcomes.
- Added `RuntimeContext` shell/filesystem normalization helpers (`withFilesystem`, `runShell`) and migrated handler-side error handling to keep behavior unchanged while simplifying failure paths.
