package io.worxbend.dotbot

import io.worxbend.dotbot.core.ShellEntryOptions
import io.worxbend.dotbot.core.CleanEntry
import io.worxbend.dotbot.core.CleanHandler
import io.worxbend.dotbot.core.CleanSpec
import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.CreateEntry
import io.worxbend.dotbot.core.CreateHandler
import io.worxbend.dotbot.core.CreateSpec
import io.worxbend.dotbot.core.describe
import io.worxbend.dotbot.core.Outcome
import io.worxbend.dotbot.core.ShellEntry
import io.worxbend.dotbot.core.ShellHandler
import io.worxbend.dotbot.core.ShellSpec
import io.worxbend.dotbot.testkit.TestRuntime

/**
 * Apply mode is deliberately lenient: a malformed entry fails on its own instead of aborting the
 * whole run. That only helps if the user is told which entry it was — a bare "some paths were not
 * set up" with nothing above it leaves them to guess.
 */
class InvalidEntrySuite extends munit.FunSuite:
  test("an invalid create entry is named in the output") {
    val runtime = TestRuntime()

    assertEquals(
      CreateHandler().execute(runtime.ctx, CreateSpec(Vector(CreateEntry.Invalid("42")))),
      Outcome.Failed,
    )
    assert(runtime.output.toString.contains("Skipping create entry that is not a path: 42"), runtime.output.toString)
  }

  test("an invalid clean entry is named in the output") {
    val runtime = TestRuntime()

    assertEquals(
      CleanHandler().execute(runtime.ctx, CleanSpec(Vector(CleanEntry.Invalid("true")))),
      Outcome.Failed,
    )
    assert(runtime.output.toString.contains("Skipping clean entry that is not a path: true"), runtime.output.toString)
  }

  test("a shell entry without a command is named in the output") {
    val runtime = TestRuntime()

    assertEquals(
      ShellHandler().execute(runtime.ctx, ShellSpec(Vector(ShellEntry.Invalid("""{description: "no command"}""")))),
      Outcome.Failed,
    )
    assert(runtime.output.toString.contains("Skipping shell entry without a command"), runtime.output.toString)
  }

  test("one bad entry does not stop the good ones around it") {
    val runtime = TestRuntime()
    val spec    = ShellSpec(
      Vector(
        ShellEntry
          .Command("echo first", "", ShellEntryOptions(quiet = false, stdin = false, stdout = false, stderr = false)),
        ShellEntry.Invalid("null"),
        ShellEntry.Command(
          "echo last",
          "",
          ShellEntryOptions(quiet = false, stdin = false, stdout = false, stderr = false),
        ),
      ),
    )

    assertEquals(ShellHandler().execute(runtime.ctx, spec), Outcome.Failed)

    val output = runtime.output.toString
    assert(output.contains("echo first"), output)
    assert(output.contains("echo last"), output)
  }

  test("a value is described the way the user wrote it") {
    assertEquals(ConfigValue.NullValue.describe, "null")
    assertEquals(ConfigValue.BoolValue(true).describe, "true")
    assertEquals(ConfigValue.StringValue("a").describe, "\"a\"")
    assertEquals(
      ConfigValue.ArrayValue(Vector(ConfigValue.StringValue("a"), ConfigValue.NullValue)).describe,
      """["a", null]""",
    )
    assertEquals(
      ConfigValue.ObjectValue(Vector("k" -> ConfigValue.NumberValue(BigDecimal(1)))).describe,
      "{k: 1}",
    )
  }
