package io.worxbend.dotbot

import io.worxbend.dotbot.core.Outcome
import io.worxbend.dotbot.testkit.TestRuntime

class OutcomeSuite extends munit.FunSuite:
  test("combine keeps success only when both outcomes succeed") {
    assertEquals(Outcome.Ok.combine(Outcome.Ok), Outcome.Ok)
    assertEquals(Outcome.Ok.combine(Outcome.Failed), Outcome.Failed)
    assertEquals(Outcome.Failed.combine(Outcome.Ok), Outcome.Failed)
    assertEquals(Outcome.Failed.combine(Outcome.Failed), Outcome.Failed)
  }

  test("withSummary logs the matching summary and preserves the outcome") {
    val okRuntime = TestRuntime()
    val failedRuntime = TestRuntime()

    assertEquals(Outcome.Ok.withSummary(okRuntime.ctx.log, "all good", "failed"), Outcome.Ok)
    assertEquals(Outcome.Failed.withSummary(failedRuntime.ctx.log, "all good", "failed"), Outcome.Failed)

    assertEquals(okRuntime.output.toString, "info  all good\n")
    assertEquals(failedRuntime.output.toString, "error failed\n")
  }
