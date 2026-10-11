# Security assumptions

Dotbot applies a program supplied by the user. Its assets are the existing dotfiles, source repository, backup files, directory permissions, and terminal output. Configuration paths and file names may contain malformed or misleading text even when the repository is trusted.

## Trust boundary

Apply mode deliberately executes `shell` commands and link `if` conditions through `/bin/sh` with the user's privileges. Configuration is executable input, not a sandboxed document. Review repositories before applying them. The tool has no authentication service, remote control plane, or need for cloud/infrastructure hardening.

Plan and validation decode configuration without executing its shell commands. Dry-run also suppresses link conditions and filesystem mutations. All three modes can still read configuration and filesystem metadata. HOCON retains the underlying parser's include behavior.

## Implemented protections

- A failed backup stops the link entry. Existing backup destinations are not overwritten, including dangling symlinks.
- Destination preparation checks source identity and canonical containment before moving or deleting existing content. Inspection failures stop replacement.
- Relink only replaces symlinks. Force is required for real files or directories; dry-run uses the same eligibility rule.
- Omitted directory modes preserve the process umask. Explicit modes apply only to newly created target directories. Validation rejects negative modes and unsupported special bits.
- Validation checks complete defaults blocks and glob syntax. Invalid paths and shell launch failures become error values.
- Failed or timed-out link conditions cannot be reported as successful skips. Command execution has a configurable timeout and process cleanup.
- Human-readable logs and plans escape terminal controls and embedded newlines. JSON preserves original values using JSON escaping. Explicitly enabled child-process output remains the child's raw output.

## Limits

The filesystem port uses ordinary Java NIO operations. Checks and subsequent mutations are not one atomic filesystem transaction. Concurrent hostile modification of source or destination directories can race those checks. Use a stable, trusted directory tree and avoid running with greater privileges than the dotfiles require.

Force without backup intentionally permits deletion. A later link-creation failure cannot restore content deleted under that option. A successful backup remains available if creating the new link fails; the tool does not automatically roll back the whole configuration.

Timeout cleanup covers the active subprocess and observable descendants. It is not an operating-system process sandbox and cannot guarantee containment of programs that deliberately detach themselves. Child-output forwarding uses the process's stdout/stderr; custom application logger streams do not redirect it.

Configuration size and recursive glob result counts are not capped. Deep or enormous trusted trees can consume memory and time. Normal globs prune traversal depth, but `**` still requests a recursive walk. There is no full-screen TUI or rendering event loop.

## Verification

`SafetyRegressionSuite` exercises destructive failure paths against temporary real files. Handler, parser, path, and terminal suites cover error contracts with isolated fixtures. `scripts/smoke_cli.py` checks the assembled/native artifact under `umask 077` and tests backup failure, dry-run parity, strict validation, and all four parsers.

These checks are application hardening, not a complete dependency-advisory audit, penetration test, or proof against concurrent filesystem attackers. JVM and native artifacts have separate verification gates in CI.
