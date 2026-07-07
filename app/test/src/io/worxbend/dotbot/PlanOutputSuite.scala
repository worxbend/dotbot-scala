package io.worxbend.dotbot

import io.worxbend.dotbot.app.PlanOutput
import io.worxbend.dotbot.core.DetailStyle
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.Operation
import io.worxbend.dotbot.core.Plan

class PlanOutputSuite extends munit.FunSuite:
  test("text output preserves header and operation detail styles") {
    val plan = Plan(
      Vector(
        Operation(Directive.Create, "generated"),
        Operation(Directive.Link, "linked.txt", "source.txt", DetailStyle.LinkTarget),
        Operation(Directive.Shell, "echo ok", "Echo", DetailStyle.ShellDescription),
      ),
    )

    assertEquals(
      PlanOutput.text(plan, taskCount = 3, configFileCount = 1, base = "/tmp/home"),
      """Plan: 3 operation(s), 3 task(s), 1 config file(s), base /tmp/home
        |create  generated
        |link    linked.txt -> source.txt
        |shell   echo ok [Echo]
        |""".stripMargin,
    )
  }

  test("json output preserves key order and trailing newline") {
    val plan = Plan(Vector(Operation(Directive.Link, "linked.txt", "source.txt", DetailStyle.LinkTarget)))

    assertEquals(
      PlanOutput.json(plan, taskCount = 1, configFileCount = 1, base = "/tmp/home"),
      """{
        |  "task_count": 1,
        |  "config_file_count": 1,
        |  "operation_count": 1,
        |  "base": "/tmp/home",
        |  "operations": [
        |    {
        |      "directive": "link",
        |      "target": "linked.txt",
        |      "detail": "source.txt"
        |    }
        |  ]
        |}
        |""".stripMargin,
    )
  }
