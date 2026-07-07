# Refactor Findings

Objective source: `/home/worxbend/.codex/attachments/75e7b53b-aa47-4916-be62-fba0527cb9ab/pasted-text-1.txt`

## Scope notes
- The repository is `dotbot-scala` and does not contain `PLAN.md`, `MEMORY.md`, `AGENT_LOG.md`, `api-snapshot/`, or `plugin-redoc-2.yaml`.
- Behavior-contract constraints come from golden tests (`app/test/src/io/worxbend/dotbot/golden/*`) and CLI tests (`app/test/src/io/worxbend/dotbot/golden/GoldenCliSuite.scala`), plus format tests in `ConfigReaderSuite` and `DotbotAppSuite`.
- What I will **not** change:
  - Public CLI behavior and output format (text/json plan, exit codes, errors, option parsing).
  - Config compatibility/parse contracts tested in golden and unit suites.
  - Existing ABI expectations: there is no API compatibility snapshot mechanism in this repo.

## Phase 1 — Findings (refactoring.guru smell mapping)

1) Duplicate structure in handler execution
- Smell(s): Large Class, Feature Envy (repeated across handlers)
- Evidence:
  - `app/src/io/worxbend/dotbot/core/CleanHandler.scala:35`
  - `app/src/io/worxbend/dotbot/core/CreateHandler.scala:27`
  - `app/src/io/worxbend/dotbot/core/ShellHandler.scala:26`
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:19`
- Candidate technique: Template Method for common directive execution skeleton, concrete hooks for entry mapping/dispatch.
- Candidate pattern reference: Refactoring.Guru Template Method
  - https://refactoring.guru/design-patterns/template-method
- Candidate refactoring technique: Extract Method + Replace Conditional with Polymorphism (for per-entry behavior).
  - https://refactoring.guru/refactoring/smells?search=duplicate-code
- Expected blast radius: Medium, localized in directive handler layer; tests in `HandlerUnitSuite`, golden command output should remain unchanged.
- ABI impact: No intentional public API signature changes.

2) Shotgun surgery when adding/reworking a directive
- Smell(s): Shotgun Surgery, Rigid Object (single change touches many files)
- Evidence:
  - New/changed directive currently touches multiple spots:
    - `app/src/io/worxbend/dotbot/core/Directive.scala:3`
    - `app/src/io/worxbend/dotbot/core/DirectiveDecoders.scala:3`
    - `app/src/io/worxbend/dotbot/app/Wiring.scala:12`
    - `app/src/io/worxbend/dotbot/core/DirectiveDefaults.scala:1`
    - `app/test/src/io/worxbend/dotbot/golden/*`
- Candidate technique: Encapsulate registration and defaults in a directive registry abstraction.
- Candidate pattern: Facade/Factory (registry-driven dispatch)
  - https://refactoring.guru/design-patterns/facade
  - https://refactoring.guru/design-patterns/factory-method
- Expected blast radius: Medium, cross `core` and `app`; would reduce future touch points.
- ABI impact: None if existing directive strings/outputs remain unchanged.

3) Primitive obsession for path/command/file arguments in core flow
- Smell(s): Primitive Obsession
- Evidence:
  - `app/src/io/worxbend/dotbot/core/ConfigDecoder.scala:3`
  - `app/src/io/worxbend/dotbot/core/DirectiveSpec.scala:1`
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:59`
  - `app/src/io/worxbend/dotbot/core/PathUtil.scala:1`
  - `app/src/io/worxbend/dotbot/core/Ports.scala:13`
- Candidate technique: Introduce small value types for command/path semantics where risk is highest (e.g., `SourcePath`, `LinkPath`, `CommandText`) and provide typed conversions at boundaries.
- Candidate pattern reference: Domain Model refactoring (not a classic GoF pattern, but aligned with GoF object-oriented encapsulation).
  - https://refactoring.guru/smells/primitive-obsession
- Expected blast radius: High, because it touches decoder, handler, and test surfaces.
- ABI impact: Keep public behavior identical; avoid changing serialized outputs (`DetailStyle`/plan fields remain strings for now).

4) Divergent error handling across execution layers
- Smell(s): Alternative Classes with Different Interfaces / Inconsistent Error Handling
- Evidence:
  - `app/src/io/worxbend/dotbot/core/ConfigDecoder.scala:3`
  - `app/src/io/worxbend/dotbot/core/ConfigDecoder.scala:17`
  - `app/src/io/worxbend/dotbot/core/Interpreter.scala:80`
  - `app/src/io/worxbend/dotbot/core/RuntimeContext.scala:23`
- Candidate technique: Introduce explicit boundary adapter that maps transport/runtime errors into `DotbotError` before they reach interpreter handlers.
- Candidate pattern: Adapter + Facade for error normalization around ports.
  - https://refactoring.guru/design-patterns/adapter
  - https://refactoring.guru/design-patterns/facade
- Expected blast radius: Medium to high in `core/Interpreter.scala`, `core/*Handler.scala`, and runtime port wrappers.
- ABI impact: None if caller-facing output remains unchanged.

5) Large method/branching in `LinkHandler`
- Smell(s): Long Method, Complex Method, Too Many Responsibilities
- Evidence:
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:19`
- Candidate technique: Extract methods + introduce strategy/state object for link lifecycle step composition.
- Candidate pattern reference:
  - https://refactoring.guru/refactoring/smells/large-class
  - https://refactoring.guru/design-patterns/chain-of-responsibility
- Expected blast radius: Medium; file-local only.
- ABI impact: No intended external interface change.

## Ranking (for execution order)
1. Duplicate handler execution structure (correctness/consistency first)
2. Divergent error handling (correctness/ergonomics of failure paths)
3. Shotgun surgery in directive onboarding (consistency/maintainability)
4. Large `LinkHandler` method (readability/complexity)
5. Primitive obsession (safe modernization, after stability)

### Explicitly out-of-scope for now
- Endpoint/command contract changes (CLI flags, JSON/text formats, plan schema).
- Any behavior changes in golden fixtures.
- New dependencies or changes to third-party parser behavior.

## Phase 2 — Implementation Plan (Top-ranked items)

### Step 1 — Introduce shared batch-handler execution template
- Smell removed: Duplicate structure in handler execution.
- Refactoring/pattern: Template Method + extract helper class/trait.
- Guru URLs: 
  - https://refactoring.guru/design-patterns/template-method
  - https://refactoring.guru/refactoring/techniques/extract-method
- Files to touch:
  - `app/src/io/worxbend/dotbot/core/DirectiveHandler.scala` (add generic batch execution hook if needed)
  - `app/src/io/worxbend/dotbot/core/CreateHandler.scala`
  - `app/src/io/worxbend/dotbot/core/CleanHandler.scala`
  - `app/src/io/worxbend/dotbot/core/ShellHandler.scala`
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala` (optional, only to consume shared template)
- Test strategy:
  - Unit suite: `HandlerUnitSuite.scala`, `InterpreterSuite.scala`
  - Golden suite: `app/test/src/io/worxbend/dotbot/golden/*`
- ABI impact:
  - No public API signature changes.
  - Behavior contract preserved; golden outputs should remain byte-for-byte.

- Completion note:
  - Added `BatchedDirectiveHandler` in `DirectiveHandler.scala`.
  - Migrated `CreateHandler`, `CleanHandler`, `ShellHandler`, and `LinkHandler` to the shared batched execute-and-summary template.
  - Validation run: `./mill --no-server __.compile` and `./mill --no-server __.test` pass after migration.

### Step 2 — Normalize error flow at port boundaries
- Smell removed: Divergent error handling.
- Refactoring/pattern: Adapter + Facade around filesystem/shell execution boundaries.
- Guru URLs:
  - https://refactoring.guru/design-patterns/adapter
  - https://refactoring.guru/design-patterns/facade
- Files to touch:
  - `app/src/io/worxbend/dotbot/core/Ports.scala`
  - `app/src/io/worxbend/dotbot/core/Interpreter.scala`
  - `app/src/io/worxbend/dotbot/core/CleanHandler.scala`
  - `app/src/io/worxbend/dotbot/core/CreateHandler.scala`
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala`
  - `app/src/io/worxbend/dotbot/core/ShellHandler.scala`
- Test strategy:
  - `DirectiveDecoderSuite`, `ConfigReaderSuite` should remain unchanged.
  - `InterpreterSuite`, `HandlerUnitSuite`, `GoldenCliSuite`, `GoldenAppSuite` to assert no observable output change.
- ABI impact:
  - No public interface changes.
  - No public ABI snapshot in repo; if one is added later, maintain current contracts.

- Completion note:
  - Added error-normalization helpers to `RuntimeContext` and switched handlers to use them for filesystem/shell execution boundaries.
  - Validation run: `./mill --no-server __.compile` and `./mill --no-server __.test` pass after migration.

### Step 3 — Reduce directive onboarding blast radius with a handler registry
- Smell removed: Shotgun surgery for new directive support.
- Refactoring/pattern: Facade/Registry + Factory for handler construction and defaults binding.
- Guru URLs:
  - https://refactoring.guru/design-patterns/facade
  - https://refactoring.guru/design-patterns/factory-method
- Files to touch:
  - `app/src/io/worxbend/dotbot/core/Directive.scala`
  - `app/src/io/worxbend/dotbot/core/DirectiveDecoders.scala`
  - `app/src/io/worxbend/dotbot/app/Wiring.scala`
  - likely supporting tests in decoder/interpreter suites if type-level assumptions change.
- Test strategy:
  - Add/adjust focused unit tests around handler lookup and defaults derivation.
  - Run existing directive integration/golden tests.
- ABI impact:
  - Keep directive labels and behavior intact.
  - No public signatures expected to change.

- Completion note:
  - Added `app/src/io/worxbend/dotbot/core/DirectiveRegistry.scala` as the single bootstrap registration point for built-in handlers.
  - Updated `app/src/io/worxbend/dotbot/app/Wiring.scala` to source handlers from the registry, keeping behavior/output unchanged.
  - Added registry-focused coverage in `app/test/src/io/worxbend/dotbot/WiringSuite.scala`.
  - Validation run: `./mill --no-server __.compile` and `./mill --no-server __.test` pass.

- Step 3 status: completed

### Step 4 — Finalization
- Updated `CHANGELOG.md` under `internal/refactor` and added one-line learnings to `MEMORY.md`.

## Phase 1 (Deep review pass) — Additional hotspots

6) CLI command construction duplication and mutable builder coupling
- Smell(s): Duplicated Structure, Incomplete Abstraction
- Evidence:
  - `app/src/io/worxbend/dotbot/cli/Cli.scala:129`
  - `app/src/io/worxbend/dotbot/cli/Cli.scala:135`
  - `app/src/io/worxbend/dotbot/cli/Cli.scala:143`
  - `app/src/io/worxbend/dotbot/cli/Cli.scala:149`
- Candidate technique/pattern: Builder + Template Method for subcommand setup.
- Expected blast radius: Medium; confined to CLI wiring and parse option plumbing.
- ABI impact: No public contract changes expected, only internal composition refactor.

7) `ConfigParsers` as a god-object over multiple parser formats and conversion concerns
- Smell(s): Large Class, Feature Envy
- Evidence:
  - `app/src/io/worxbend/dotbot/config/ConfigParsers.scala:22`
  - `app/src/io/worxbend/dotbot/config/ConfigParsers.scala:52`
  - `app/src/io/worxbend/dotbot/config/ConfigParsers.scala:63`
  - `app/src/io/worxbend/dotbot/config/ConfigParsers.scala:82`
  - `app/src/io/worxbend/dotbot/config/ConfigParsers.scala:156`
- Candidate technique/pattern: Strategy/Plugin for format parsers + dedicated converters per input type.
- Expected blast radius: Medium; parser behavior touches `ConfigParsers`, `ConfigValue`, and parser tests.
- ABI impact: No format/CLI contract changes if wire format rendering is preserved.

8) Link execution flow remains high-complexity (branching, policy, and filesystem orchestration)
- Smell(s): Large Class, Long Method, Complex Conditional
- Evidence:
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:42`
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:59`
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:99`
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:191`
- Candidate technique/pattern: State/Strategy objects for link lifecycle operations (check, backup, remove, create).
- Expected blast radius: Medium-to-high; one directive only but tightly coupled to filesystem/shell behavior and defaults.
- ABI impact: No API contract changes.

9) Inconsistent execution policy use around shell invocation in link conditions
- Smell(s): Inconsistent Abstraction and Error Handling
- Evidence:
  - `app/src/io/worxbend/dotbot/core/LinkHandler.scala:55`
  - `app/src/io/worxbend/dotbot/core/RuntimeContext.scala:35`
  - `app/src/io/worxbend/dotbot/core/RuntimeContext.scala:23`
- Candidate technique/pattern: Adapter for shell execution normalization.
- Expected blast radius: Low-to-medium; single helper class and single path through shell usage.
- ABI impact: No outward interface change; logs/output should remain stable.

10) Silent config coercion hides malformed input in link defaults
- Smell(s): Error Swallowing / Speculative Generality boundary
- Evidence:
  - `app/src/io/worxbend/dotbot/core/LinkOptions.scala:59`
  - `app/src/io/worxbend/dotbot/core/LinkOptions.scala:64`
  - `app/src/io/worxbend/dotbot/core/LinkOptions.scala:67`
- Candidate technique/pattern: Validation with explicit failure paths (fail-fast decoding) or strict parser mode for user-facing config fields.
- Expected blast radius: Low if limited to link defaults; medium if promoted to shared config conversion policy.
- ABI impact: Could change error surfaces for invalid config input; needs golden coverage.

11) Unnecessary full plan computation in invalid format path
- Smell(s): Performance Smell, Redundant Work
- Evidence:
  - `app/src/io/worxbend/dotbot/app/AppCommand.scala:73`
  - `app/src/io/worxbend/dotbot/app/AppCommand.scala:74`
  - `app/src/io/worxbend/dotbot/app/AppCommand.scala:79`
- Candidate technique/pattern: Fail-fast on invalid plan output before dispatching planning logic.
- Expected blast radius: Very low; only command routing behavior for invalid plan format.
- ABI impact: CLI-visible behavior should stay equivalent (`unsupported output format` error).

### Re-ranked priority (deep review)
1. CLI command construction duplication (`Cli.scala`) before broader parser refactors.
2. `ConfigParsers` consolidation and format-converter decomposition.
3. Remaining `LinkHandler` lifecycle complexity and inconsistent shell execution path.
4. Input validation quality in `LinkOptions` coercion.
5. Error-path inefficiency in `AppCommand.invalidPlanOutput`.
