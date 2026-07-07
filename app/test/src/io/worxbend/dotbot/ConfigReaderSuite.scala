package io.worxbend.dotbot

import io.worxbend.dotbot.config.ConfigReader
import io.worxbend.dotbot.config.ConfigParsers
import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.DotbotError

class ConfigReaderSuite extends munit.FunSuite:
  test("reads YAML, HOCON, JSON, and TOML examples with directive order preserved") {
    val reader = ConfigReader()
    val root = os.temp.dir(prefix = "dotbot-scala-config-")
    val examples = Vector(
      "install.conf.yaml" ->
        """- defaults:
          |    link:
          |      create: true
          |- clean:
          |    - "~"
          |- create:
          |    - generated
          |- link:
          |    linked.txt: source.txt
          |- shell:
          |    - [echo ok, Echo]
          |""".stripMargin,
      "install.json" ->
        """[
          |  {"defaults": {"link": {"create": true}}},
          |  {"clean": ["~"]},
          |  {"create": ["generated"]},
          |  {"link": {"linked.txt": "source.txt"}},
          |  {"shell": [["echo ok", "Echo"]]}
          |]
          |""".stripMargin,
      "install.conf" ->
        """tasks = [
          |  { defaults = { link = { create = true } } }
          |  { clean = ["~"] }
          |  { create = ["generated"] }
          |  { link = { "linked.txt" = "source.txt" } }
          |  { shell = [["echo ok", "Echo"]] }
          |]
          |""".stripMargin,
      "install.toml" ->
        """tasks = [
          |  { defaults = { link = { create = true } } },
          |  { clean = ["~"] },
          |  { create = ["generated"] },
          |  { link = { "linked.txt" = "source.txt" } },
          |  { shell = [["echo ok", "Echo"]] },
          |]
          |""".stripMargin,
    )

    for (file, content) <- examples do
      val path = root / file
      os.write(path, content)
      val tasks = reader.read(Vector(path.toString)).fold(error => fail(error.render), identity)

      assertEquals(tasks.map(_.actions.map(_.directive.label)), Vector(Vector("defaults"), Vector("clean"), Vector("create"), Vector("link"), Vector("shell")))
  }

  test("reads HOCON root task arrays through Lightbend Config") {
    val root = os.temp.dir(prefix = "dotbot-scala-hocon-array-")
    val path = root / "install.hocon"
    os.write(
      path,
      """[
        |  { defaults = { create = { mode = 493 } } }
        |  { create = { generated = { mode = 448 } } }
        |]
        |""".stripMargin,
    )

    val tasks = ConfigReader().read(Vector(path.toString)).fold(error => fail(error.render), identity)
    val mode = tasks.head.actions.head.data
      .field("create")
      .flatMap(_.field("mode"))

    assertEquals(mode, Some(ConfigValue.NumberValue(BigDecimal(493))))
  }

  test("accepts YAML and HOCON extension aliases") {
    val ymlTasks = ConfigParsers.parseTasks("install.yml", "yml", "- create: [generated]\n")
      .fold(error => fail(error.render), identity)
    val hoconTasks = ConfigParsers.parseTasks("install.hocon", "hocon", """tasks = [{ create = ["generated"] }]""")
      .fold(error => fail(error.render), identity)

    assertEquals(ymlTasks.map(_.actions.map(_.directive.label)), Vector(Vector("create")))
    assertEquals(hoconTasks.map(_.actions.map(_.directive.label)), Vector(Vector("create")))
  }

  test("reads JSON root task objects through Lightbend Config and PureConfig") {
    val tasks = ConfigParsers.parseTasks(
      "install.json",
      "json",
      """{
        |  "tasks": [
        |    { "create": ["generated"] }
        |  ]
        |}
        |""".stripMargin,
    ).fold(error => fail(error.render), identity)

    assertEquals(tasks.map(_.actions.map(_.directive.label)), Vector(Vector("create")))
  }

  test("reads HOCON task alias through Lightbend Config and PureConfig") {
    val tasks = ConfigParsers.parseTasks(
      "install.conf",
      "conf",
      """task = [
        |  { shell = ["echo ok"] }
        |]
        |""".stripMargin,
    ).fold(error => fail(error.render), identity)

    assertEquals(tasks.map(_.actions.map(_.directive.label)), Vector(Vector("shell")))
  }

  test("preserves directive key order within a task") {
    val root = os.temp.dir(prefix = "dotbot-scala-key-order-")
    val path = root / "install.conf.yaml"
    os.write(
      path,
      """- create: []
        |  clean: []
        |  shell: []
        |""".stripMargin,
    )

    val tasks = ConfigReader().read(Vector(path.toString)).fold(error => fail(error.render), identity)

    assertEquals(tasks.head.actions.map(_.directive.label), Vector("create", "clean", "shell"))
  }

  test("parses YAML scalars without file I/O") {
    val value = ConfigParsers.parseValue(
      "values.yaml",
      "yaml",
      """mode: 0x1ed
        |enabled: true
        |empty:
        |name: dotbot
        |""".stripMargin,
    )

    assertEquals(
      value,
      Right(
        ConfigValue.ObjectValue(
          Vector(
            "mode" -> ConfigValue.NumberValue(BigDecimal(493)),
            "enabled" -> ConfigValue.BoolValue(true),
            "empty" -> ConfigValue.NullValue,
            "name" -> ConfigValue.StringValue("dotbot"),
          ),
        ),
      ),
    )
  }

  test("parses null configs as an empty task list") {
    assertEquals(ConfigParsers.parseTasks("empty.yaml", "yaml", ""), Right(Vector.empty))
    assertEquals(ConfigParsers.parseTasks("empty.json", "json", "null"), Right(Vector.empty))
    assertEquals(ConfigParsers.parseTasks("empty.conf", "conf", "null"), Right(Vector.empty))
  }

  test("does not treat hocon keys starting with null as a root null value") {
    assertEquals(
      ConfigParsers.parseTasks("bad.conf", "conf", "nullish = true"),
      Left(DotbotError.ConfigRootNotTaskList("bad.conf")),
    )
  }

  test("reports bad root and task shapes from pure parsers") {
    assertEquals(
      ConfigParsers.parseTasks("bad.conf", "conf", "name = value"),
      Left(DotbotError.ConfigRootNotTaskList("bad.conf")),
    )
    assertEquals(
      ConfigParsers.parseTasks("bad.json", "json", "[1]"),
      Left(DotbotError.ConfigTaskNotMapping("bad.json", 1)),
    )
  }

  test("reads TOML task alias and values through tomlj") {
    val tasks = ConfigParsers.parseTasks(
      "install.toml",
      "toml",
      """task = [
        |  { defaults = { create = { mode = 493 } } },
        |  { create = { generated = { mode = "700" } } },
        |]
        |""".stripMargin,
    ).fold(error => fail(error.render), identity)

    val numericMode = tasks.head.actions.head.data
      .field("create")
      .flatMap(_.field("mode"))
    val stringMode = tasks(1).actions.head.data
      .field("generated")
      .flatMap(_.field("mode"))

    assertEquals(numericMode, Some(ConfigValue.NumberValue(BigDecimal(493))))
    assertEquals(stringMode, Some(ConfigValue.StringValue("700")))
  }

  test("rejects JSON5 config file extension") {
    assertEquals(
      ConfigParsers.parseTasks("install.json5", "json5", "[]"),
      Left(DotbotError.UnsupportedConfigFormat("json5")),
    )
  }
