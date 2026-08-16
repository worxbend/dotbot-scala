package io.worxbend.dotbot.cli

import io.worxbend.dotbot.app.OutputFormat
import io.worxbend.dotbot.app.OutputFormatArgument
import io.worxbend.dotbot.app.RunMode
import io.worxbend.dotbot.core.Directive

/**
 * Unit tests for CLI argument interpretation. These live in the `cli` package so they can reach
 * the parser's own state type rather than going through a full `Cli.execute` round trip.
 */
class CliSuite extends munit.FunSuite:
  test("a directive list is parsed into typed directives") {
    assertEquals(Cli.splitDirectiveList("clean,link"), Right(Vector(Directive.Clean, Directive.Link)))
  }

  test("surrounding whitespace and empty entries are ignored") {
    assertEquals(Cli.splitDirectiveList(" clean , link ,"), Right(Vector(Directive.Clean, Directive.Link)))
    assertEquals(Cli.splitDirectiveList(""), Right(Vector.empty))
  }

  test("an unknown directive name is rejected rather than invented") {
    // Accepting this used to make the filter match nothing, so every action was skipped and the
    // run still reported success.
    assertEquals(Cli.splitDirectiveList("lnik"), Left(Vector("lnik")))
  }

  test("every unknown name is reported, not just the first") {
    assertEquals(Cli.splitDirectiveList("clean,lnik,shel"), Left(Vector("lnik", "shel")))
  }

  test("a shell timeout is read as whole seconds") {
    assertEquals(Cli.parseShellTimeout("30"), Some(java.time.Duration.ofSeconds(30)))
  }

  test("a shell timeout that is not a positive number is rejected rather than defaulted") {
    // Falling back to the default would look exactly like the flag working, and would surface
    // only as a hang much later.
    assertEquals(Cli.parseShellTimeout("abc"), None)
    assertEquals(Cli.parseShellTimeout("0"), None)
    assertEquals(Cli.parseShellTimeout("-5"), None)
  }

  test("directive names are matched exactly") {
    assertEquals(Cli.splitDirectiveList("Link"), Left(Vector("Link")))
    assertEquals(Cli.splitDirectiveList("links"), Left(Vector("links")))
  }

  test("subcommand state inherits the root command's options") {
    val root = CliState(
      quiet = true,
      verbose = 1,
      baseDirectory = "/root/base",
      configFiles = List("root.yaml"),
      only = Vector(Directive.Link),
      dryRun = true,
    )
    val merged = CliState().mergedWith(root)

    assertEquals(merged.quiet, true)
    assertEquals(merged.verbose, 1)
    assertEquals(merged.baseDirectory, "/root/base")
    assertEquals(merged.configFiles, List("root.yaml"))
    assertEquals(merged.only, Vector(Directive.Link))
    assertEquals(merged.dryRun, true)
  }

  test("verbosity accumulates across the root command and its subcommand") {
    val merged = CliState(verbose = 2).mergedWith(CliState(verbose = 1))

    assertEquals(merged.verbose, 3)
  }

  test("a subcommand base directory overrides the root one") {
    val merged = CliState(baseDirectory = "/sub").mergedWith(CliState(baseDirectory = "/root"))

    assertEquals(merged.baseDirectory, "/sub")
  }

  test("config files from both commands are kept") {
    val merged = CliState(configFiles = List("sub.yaml")).mergedWith(CliState(configFiles = List("root.yaml")))

    assertEquals(merged.configFiles.toSet, Set("sub.yaml", "root.yaml"))
  }

  test("boolean flags are true if either command set them") {
    val merged = CliState(noColor = true).mergedWith(CliState(exitOnFailure = true))

    assertEquals(merged.noColor, true)
    assertEquals(merged.exitOnFailure, true)
  }

  test("unknown directives from both commands are collected") {
    val merged = CliState(unknownDirectives = Vector("sub")).mergedWith(CliState(unknownDirectives = Vector("root")))

    assertEquals(merged.unknownDirectives.toSet, Set("sub", "root"))
  }

  test("an explicit subcommand output format wins over the inherited default") {
    val merged =
      CliState(planOutput = OutputFormatArgument.Valid(OutputFormat.Json))
        .mergedWith(CliState(planOutput = OutputFormatArgument.Valid(OutputFormat.Text)))

    assertEquals(merged.planMode, RunMode.Plan(OutputFormat.Json))
  }

  test("config files reach AppOptions in the order they were given") {
    // The builder prepends as it parses, so the list is reversed on the way out.
    val state = CliState(configFiles = List("second.yaml", "first.yaml"))

    assertEquals(state.appOptions().configFiles, Vector("first.yaml", "second.yaml"))
  }

  test("the run mode is carried into AppOptions unchanged") {
    assertEquals(CliState().appOptions().mode, RunMode.Apply)
    assertEquals(CliState().appOptions(RunMode.Validate).mode, RunMode.Validate)
  }
