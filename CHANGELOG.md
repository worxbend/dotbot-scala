# Changelog

## 0.2.2

No user-visible changes. Same commands, same output, same exit codes -- this release is entirely
internal cleanup, verified against the golden test suite that asserts exact stdout, stderr and
exit codes for every command.

### Internal

- **The filesystem port no longer speaks in Java exceptions.** Its methods returned
  `Either[Throwable, A]`, but every caller reduced that to the exception's message and nothing
  ever inspected the type. It now returns a plain `FsFailure(message)`, so the domain layer names
  no JVM type and test fakes no longer invent exceptions to satisfy a signature.
- **Link creation is readable.** The function deciding what to do when a link path is occupied was
  fifty lines with a four-line boolean guard and a filesystem call embedded inside a condition. It
  is now a three-line dispatch over three named cases.
- **Startup failures are values, not exit codes.** The startup path returned `Either[Int, _]`, so
  each failing branch had to remember both to log its message and to return an exit code. Failures
  now travel as `DotbotError` and are rendered in one place, matching the rest of the pipeline.
- **`clean` and `shell` entries carry options objects**, as `link` already did, so the
  defaults-then-override rule is stated once per directive rather than inlined per field. This
  also retires a six-field positional `ShellEntry.Command` in which two boolean flags could be
  transposed without the compiler noticing.
- **A second copy of the run modes is gone.** `AppCommand` duplicated `RunMode` case for case with
  an identity converter between them; adding a mode meant editing both plus the converter.
- **A parse failure is caught in one place.** All four config parsers ended with the same
  exception-to-error line; it now lives in the shared dispatch, so a fifth format cannot forget it.
- The containment rule that decides whether `clean` deletes a broken symlink moved into `PathUtil`
  and gained direct tests, including the sibling-prefix (`/base` vs `/basement`) and escaping-`..`
  boundaries that previously had only indirect coverage.
- Removed dead code: an unused `ShellExit.code`, a `DirectiveSpec.directive` no caller read, and a
  single-use decoder combinator that duplicated `Option.orElse`.

## 0.2.1

### Fixed

- **JSON configurations keep the order they were written in, exactly.** JSON is now parsed with
  jsoniter-scala, which reads fields in stream order, so no ordering has to be recovered after the
  fact. This removes the remaining case where a JSON task written on a single line could be
  reordered.
- **An unorderable HOCON task is reported instead of guessed at.** HOCON records only the line a
  value came from, so several directives sharing one line inside a task cannot be put back in
  order. That is now an error naming the line and the directives, rather than a silent fall back
  to hash order.

### Internal

- Dropped the PureConfig dependency; HOCON is read directly through Lightbend Config.

## 0.2.0

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
