package io.worxbend.dotbot.logging

import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LoggerSuite extends munit.FunSuite:
  test("labels are padded as part of the output contract") {
    val output = ByteArrayOutputStream()
    val logger = Logger(PrintStream(output), minimum = Level.Debug, color = false)

    logger.debug("debug")
    logger.info("info")
    logger.action("action")
    logger.warning("warning")
    logger.error("error")

    assertEquals(
      output.toString,
      """debug debug
        |info  info
        |step  action
        |warn  warning
        |error error
        |""".stripMargin,
    )
  }

  test("minimum level is fixed at construction") {
    val output = ByteArrayOutputStream()
    val logger = Logger(PrintStream(output), minimum = Level.Warning, color = false)

    logger.action("hidden")
    logger.warning("visible")

    assertEquals(output.toString, "warn  visible\n")
  }

  test("color is fixed at construction") {
    val output = ByteArrayOutputStream()
    val logger = Logger(PrintStream(output), minimum = Level.Action, color = true)

    logger.error("colored")

    assertEquals(
      output.toString,
      s"${fansi.Color.Red.escape}error${fansi.Attr.Reset.escape} ${fansi.Color.Red.escape}colored${fansi.Attr.Reset.escape}\n",
    )
  }
