package io.worxbend.dotbot.logging

import io.worxbend.dotbot.app.PlanOutput
import io.worxbend.dotbot.app.PlanSummary
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.Operation
import io.worxbend.dotbot.core.Plan

import java.io.ByteArrayOutputStream
import java.io.PrintStream

class TerminalTextSuite extends munit.FunSuite:
  test("suppressed diagnostics do not evaluate their message") {
    val stream = PrintStream(ByteArrayOutputStream())
    try Logger(stream, Level.Warning, color = false).debug(throw AssertionError("message was evaluated"))
    finally stream.close()
  }

  test("terminal controls and forged log lines are rendered visibly") {
    assertEquals(
      TerminalText.escape("name\u001b]52;clipboard\u0007\nerror forged\r\t"),
      "name\\u001b]52;clipboard\\u0007\\nerror forged\\r\\t",
    )
    assertEquals(TerminalText.escape("normal 🗺️ text"), "normal 🗺️ text")
  }

  test("the logger escapes config text before adding its own styling") {
    val output = ByteArrayOutputStream()
    val stream = PrintStream(output)
    try
      Logger(stream, Level.Info, color = false).warning("bad\u001b[2J\nforged")
      assertEquals(output.toString, "warn  bad\\u001b[2J\\nforged\n")
    finally stream.close()
  }

  test("text plans escape terminal controls while JSON preserves the underlying value") {
    val target  = "bad\u001b[2J\npath"
    val plan    = Plan(Vector(Operation(Directive.Create, target)))
    val summary = PlanSummary(1, 1, "/tmp\u0007")
    val text    = PlanOutput.text(plan, summary)
    assert(!text.contains('\u001b'))
    assert(!text.contains('\u0007'))
    assertEquals(ujson.read(PlanOutput.json(plan, summary))("operations")(0)("target").str, target)
  }
