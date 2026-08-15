package io.worxbend.dotbot.logging

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Covers which stream each level is written to, and how the badge is composed.
 * `LoggerSuite` covers the label padding contract itself.
 */
class LoggerStreamSuite extends munit.FunSuite:
  test("warnings and errors go to the error stream, everything else to output") {
    val output = ByteArrayOutputStream()
    val errors = ByteArrayOutputStream()
    val logger = Logger.split(PrintStream(output), PrintStream(errors), Level.Debug, TerminalCapabilities.plain)

    logger.debug("d")
    logger.info("i")
    logger.action("a")
    logger.warning("w")
    logger.error("e")

    // A warning must never land in the middle of piped plan output.
    assertEquals(
      output.toString,
      """debug d
        |info  i
        |step  a
        |""".stripMargin,
    )
    assertEquals(
      errors.toString,
      """warn  w
        |error e
        |""".stripMargin,
    )
  }

  test("a single-stream logger keeps writing everything to that stream") {
    val output = ByteArrayOutputStream()
    val logger = Logger(PrintStream(output), Level.Debug, TerminalCapabilities.plain)

    logger.action("a")
    logger.error("e")

    assertEquals(output.toString, "step  a\nerror e\n")
  }

  test("symbols prefix the badge only when they are enabled") {
    val plain = ByteArrayOutputStream()
    val stylish = ByteArrayOutputStream()

    Logger(PrintStream(plain), Level.Action, TerminalCapabilities(color = false, symbols = false)).action("go")
    Logger(PrintStream(stylish), Level.Action, TerminalCapabilities(color = false, symbols = true)).action("go")

    // With symbols off the line must start at column zero: a blank placeholder where the emoji
    // would go is what shifted every line of output four columns to the right.
    assertEquals(plain.toString, "step  go\n")
    assertEquals(stylish.toString, "🚀 step  go\n")
  }

  test("messages line up in the same column whether or not color is on") {
    val plain = ByteArrayOutputStream()
    val colored = ByteArrayOutputStream()

    Logger(PrintStream(plain), Level.Debug, color = false).info("aligned")
    Logger(PrintStream(colored), Level.Debug, color = true).info("aligned")

    val withoutEscapes = colored.toString.replaceAll("\\[[0-9;]*m", "")
    assertEquals(withoutEscapes, plain.toString)
  }

  test("levels below the minimum are not written to either stream") {
    val output = ByteArrayOutputStream()
    val errors = ByteArrayOutputStream()
    val logger = Logger.split(PrintStream(output), PrintStream(errors), Level.Error, TerminalCapabilities.plain)

    logger.warning("hidden")
    logger.error("shown")

    assertEquals(output.toString, "")
    assertEquals(errors.toString, "error shown\n")
  }

  test("level ranks order the levels from most to least verbose") {
    val ordered = Vector(Level.Debug, Level.Info, Level.Action, Level.Warning, Level.Error)

    assertEquals(ordered.map(_.rank), Vector(0, 1, 2, 3, 4))
    assertEquals(ordered.sortBy(_.rank), ordered)
  }
