# Codebase review — 2026-10-11

Baseline: `b96d07e` (`0.2.2`). Scope: architecture, CLI contracts, Scala/Mill structure, filesystem safety, error handling, and local performance. Changes are intentionally compatible for normal configurations; the behavior changes below correct unsafe or misleading cases.

## Findings and implementation

| Priority | Finding | Resolution |
| --- | --- | --- |
| High | A failed backup allowed forced deletion to continue. | Destination preparation stops before later mutations. A regression reproduces the filesystem filename-length failure. |
| High | Omitted directory modes were changed to `0777`, overriding umask. | `CreateEntry` carries `Option[FileMode]`; omission preserves the OS-created permissions. Explicit modes remain supported. |
| Medium | Relink dry-run predicted replacing a regular file while apply refused. | Removal eligibility is shared before either reporting or mutation. |
| Medium | Malformed defaults disappeared before validation. | A dedicated decoder checks the whole block, shapes, names, options, and link type even without later actions. |
| Medium | Malformed globs passed validation and threw during apply. | Matcher construction returns a typed error; validation checks source and exclusion patterns. Apply reports failure and can continue later directives. |
| High | Backups could overwrite another backup created within the same second. | Collision detection and non-replacing moves preserve both originals. |
| High | Backup/force could move the source itself or delete the directory containing it. | Identity and canonical containment guards stop the entry. |
| Medium | Invalid paths, non-directory bases, and shell start failures escaped normal contracts. | Path/start failures are values; startup verifies the base is a directory. |
| Medium | A link condition timeout was treated as an ordinary false condition. | Timeouts and start failures fail the directive; nonzero condition exits still skip it. |
| Medium | Config text could emit terminal controls or forged log lines. | Human output escapes control characters; JSON retains original values. |
| Medium | Negative or special permission bits could be accepted and silently truncated. | Configuration decoding supports only permission bits `0000`–`0777`. |
| Performance | Each glob exclusion repeated the filesystem walk. | One included walk; compiled matchers filter its candidates. |

## Architecture assessment

Retain the existing small ports-and-adapters design. It already supports typed decoding, explicit defaults state, polymorphic directive handlers, and injected effects. Those are useful abstractions for this CLI.

Implemented structural changes:

1. Move sources into actual `core/src`, `infra/src`, and `app/src` module roots, preserving package names. Remove source paths derived from the shell's `PWD`.
2. Extract `LinkDestination` from `LinkHandler`. Preparation owns safety checks and prerequisite ordering; the handler owns source selection and link creation.
3. Extract defaults decoding into `DirectiveDefaults`. Keep validation separate from explicitly permissive apply compatibility.
4. Add the `ConfigLoader` port so application dependencies do not require a concrete parser class.
5. Separate `CliState` and its invocation-local builder from Picocli option declarations. Remove unnecessary atomic synchronization.
6. Make omitted modes explicit in the type model and add typed glob/path/shell-start failures.
7. Use the narrower `ujson` dependency, retain the existing Scala/Ox effect model, target Java 21 in both compilers, and fail warnings locally and in CI.

The registry uses Strategy; batched handlers use a small Template Method; infrastructure implements Adapter and dependency-inversion boundaries. These existing patterns solve concrete variation points. No extra service locator, generic workflow framework, or hierarchy is needed.

## Performance evidence

Measured on the same machine with Java 21.0.8. Each sample launches a fresh JVM; one warm-up is discarded and five samples are retained. Filesystem caches are warm. No builds ran concurrently with the recorded comparison. The baseline assembly was built from an isolated archive of `b96d07e`.

| Scenario | Baseline median | Revised median |
| --- | ---: | ---: |
| Version | 182.56 ms | 181.34 ms |
| Help | 200.84 ms | 202.21 ms |
| JSON plan, 1,000 links | 305.62 ms | 307.77 ms |
| Validate, 10,000 links | 306.01 ms | 306.47 ms |
| Quiet glob dry-run, 5,000 files and 10 exclusions | 638.77 ms | 471.26 ms |

The glob scenario improved by about 26%. Other differences are small enough to treat as noise. Assembly size fell from 15,911,606 to 15,291,421 bytes (about 3.9%). This is local diagnostic evidence, not a performance guarantee or a native-binary benchmark.

`GlobTraversalSuite` also verifies the structural improvement: one walk, regardless of exclusion count. Suppressed log messages are lazy, avoiding construction of large debug strings. Terminal rendering remains a one-pass CLI operation; there is no full-screen TUI to optimize.

Raw samples: [benchmark results](benchmarks/2026-10-11.json). Reproduce with `scripts/benchmark_cli.py`.

## Future design boundaries

- Keep `plan` documented as a summary of validated intent. A future executable-plan API would need explicit create/remove/backup effects, resolved paths, preconditions, and revalidation before apply. Adding that now would change its JSON contract substantially.
- If embedding becomes a public product, supply child-output streams through a dedicated shell adapter and define source/binary compatibility gates. The current project ships a CLI, not published library artifacts.
- A filesystem transaction or hostile-directory guarantee requires stronger OS-specific primitives and ownership rules. Ordinary `exists`/`move`/`delete` calls cannot provide it. Current guarantees and limits are in [SECURITY.md](SECURITY.md).
- If recursive configurations grow beyond typical dotfiles repositories, add measured streaming traversal or configurable budgets. Preserve deterministic order and resource closure.
- Keep the single `app.test` entrypoint while the suite is small. Split test modules when ownership or runtime costs justify separate execution, rather than multiplying modules for symmetry.

## Verification results

- Full MUnit/ScalaCheck suite: **279 tests, zero failures, errors, or skips** (baseline: 248).
- Core Scalafix check and all four module/test formatting checks passed.
- Existing golden fixtures were unchanged. One handler expectation deliberately drops the misleading second warning after source-identity failure.
- The JVM assembly passed `scripts/smoke_cli.py` on Java 21.0.8, including `umask 077` and all four configuration formats.
- CI now checks formatting and runs the same artifact smoke suite against the JVM assembly and both native architectures.
- Native binaries were not built locally. No dependency-advisory or concurrency-attack guarantee is implied by these checks.

## Skill application

Applied the installed Caveman communication/review guidance, Scala professional/type-system/functional/collections/library-contract guidance, and relevant Mill module, build-logic, toolchain, testing, linting, packaging, and performance guidance. Clean-code, refactoring, design-pattern, CLI, and security guidance informed the implementation.

No ADHD skill was found in the installed skill directories or plugin cache. Communication uses short updates and explicit priorities. ZIO, Scala.js/Native, VSS, polyglot, cloud-hardening, and Caveman Cloud workflows do not match this Scala JVM CLI and were not introduced.

## References

- [Java 21 Files API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): directory creation, permissions, non-replacing moves, and filesystem operation limits.
- [Picocli manual](https://picocli.info/): CLI execution and error boundaries. Existing exit-code behavior is preserved.
- The checked-in Mill 1.1.6 wrapper and verified task API remain the build authority. Dependency/compiler upgrades were not required.
