package io.worxbend.dotbot

import io.worxbend.dotbot.app.AppOptions
import io.worxbend.dotbot.app.DotbotApp
import io.worxbend.dotbot.app.OutputFormat
import io.worxbend.dotbot.app.RunMode

import java.io.ByteArrayOutputStream
import java.io.PrintStream

class DotbotAppSuite extends munit.FunSuite:
  test("prints JSON plan without applying changes") {
    val root   = os.temp.dir(prefix = "dotbot-scala-plan-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |- link:
        |    linked.txt: source.txt
        |""".stripMargin,
    )

    val output = ByteArrayOutputStream()
    val code   = DotbotApp.run(
      AppOptions(configFiles = Vector(config.toString), mode = RunMode.Plan(OutputFormat.Json), noColor = true),
      PrintStream(output),
    )

    assertEquals(code, 0)
    val json = ujson.read(output.toString)
    assertEquals(json("operation_count").num.toInt, 2)
    assertEquals(json("operations")(0)("directive").str, "create")
    assertEquals(json("operations")(1)("directive").str, "link")
    assert(!os.exists(root / "generated"))
  }

  test("dry run leaves filesystem untouched") {
    val root   = os.temp.dir(prefix = "dotbot-scala-dry-run-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |""".stripMargin,
    )

    val output = ByteArrayOutputStream()
    val code   = DotbotApp.run(
      AppOptions(configFiles = Vector(config.toString), dryRun = true, noColor = true),
      PrintStream(output),
    )

    assertEquals(code, 0)
    assert(!os.exists(root / "generated"))
    assert(output.toString.contains("Would create path"))
  }

  test("creates symlinks relative to the config base directory") {
    val root   = os.temp.dir(prefix = "dotbot-scala-link-")
    os.write(root / "source.txt", "hello\n")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- defaults:
        |    link:
        |      create: true
        |- link:
        |    linked.txt: source.txt
        |""".stripMargin,
    )

    val output = ByteArrayOutputStream()
    val code   = DotbotApp.run(
      AppOptions(configFiles = Vector(config.toString), noColor = true),
      PrintStream(output),
    )

    assertEquals(code, 0)
    assert(os.isLink(root / "linked.txt"))
    assertEquals(os.read(root / "linked.txt"), "hello\n")
  }
