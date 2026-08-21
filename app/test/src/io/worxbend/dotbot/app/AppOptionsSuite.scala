package io.worxbend.dotbot.app

import io.worxbend.dotbot.logging.Level
import io.worxbend.dotbot.logging.TerminalCapabilities

/**
 * Covers how CLI options are turned into logging decisions. These live in the `app` package so
 * they can call the mapping functions directly instead of inferring them from captured output.
 */
class AppOptionsSuite extends munit.FunSuite:
  private val terminal = TerminalCapabilities(color = true, symbols = true)
  private val pipe     = TerminalCapabilities.plain

  test("verbosity selects the minimum level") {
    assertEquals(DotbotApp.minimumLevel(AppOptions()), Level.Action)
    assertEquals(DotbotApp.minimumLevel(AppOptions(verbose = 1)), Level.Info)
    assertEquals(DotbotApp.minimumLevel(AppOptions(verbose = 2)), Level.Debug)
    assertEquals(DotbotApp.minimumLevel(AppOptions(verbose = 5)), Level.Debug)
  }

  test("quiet wins over verbosity") {
    assertEquals(DotbotApp.minimumLevel(AppOptions(quiet = true)), Level.Warning)
    assertEquals(DotbotApp.minimumLevel(AppOptions(quiet = true, verbose = 2)), Level.Warning)
  }

  test("without flags the terminal decides") {
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(), terminal), terminal)
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(), pipe), pipe)
  }

  test("force overrides a terminal that cannot render, and no- overrides one that can") {
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(forceColor = true), pipe).color, true)
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(noColor = true), terminal).color, false)
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(forceEmoji = true), pipe).symbols, true)
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(noEmoji = true), terminal).symbols, false)
  }

  test("conflicting flags fall back to what the terminal supports") {
    // The logger has to exist before the conflict can be reported through it, so this path is
    // reached in practice: it decides how that very error message is rendered.
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(forceColor = true, noColor = true), terminal).color, true)
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(forceColor = true, noColor = true), pipe).color, false)
  }

  test("conflicting emoji flags fall back the same way conflicting color flags do") {
    // Color and symbols answer the same force/suppress/detected question, so a reader who has
    // learned one rule should not find the other behaving differently.
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(forceEmoji = true, noEmoji = true), terminal).symbols, true)
    assertEquals(DotbotApp.effectiveCapabilities(AppOptions(forceEmoji = true, noEmoji = true), pipe).symbols, false)
  }

  test("color and symbols are decided independently") {
    val mixed = DotbotApp.effectiveCapabilities(AppOptions(noColor = true, forceEmoji = true), pipe)

    assertEquals(mixed, TerminalCapabilities(color = false, symbols = true))
  }

  test("an output format argument is recognized, defaulted, or held as invalid") {
    assertEquals(OutputFormat.fromString("text"), OutputFormatArgument.Valid(OutputFormat.Text))
    assertEquals(OutputFormat.fromString("json"), OutputFormatArgument.Valid(OutputFormat.Json))
    // An absent --output means text, which is why the empty string is valid here.
    assertEquals(OutputFormat.fromString(""), OutputFormatArgument.Valid(OutputFormat.Text))
    assertEquals(OutputFormat.fromString("yaml"), OutputFormatArgument.Invalid("yaml"))
  }

  test("an invalid output format becomes a run mode that still reports the bad value") {
    // The value is carried through rather than rejected at parse time, so the config is still
    // planned first and a broken config is reported before a mistyped flag.
    assertEquals(OutputFormat.fromString("yaml").toRunMode, RunMode.InvalidPlanOutput("yaml"))
    assertEquals(OutputFormat.fromString("json").toRunMode, RunMode.Plan(OutputFormat.Json))
  }
