package io.worxbend.dotbot

import io.worxbend.dotbot.config.ConfigReader
import io.worxbend.dotbot.config.ConfigValue

class ConfigReaderSuite extends munit.FunSuite:
  test("reads YAML, JSON, JSON5, and TOML examples with directive order preserved") {
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
      "install.json5" ->
        """[
          |  { defaults: { link: { create: true, }, }, },
          |  { clean: ["~",], },
          |  { create: ["generated",], },
          |  { link: { "linked.txt": "source.txt", }, },
          |  { shell: [["echo ok", "Echo",],], },
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
      val tasks = reader.read(Vector(path.toString)).fold(error => fail(error), identity)

      assertEquals(tasks.map(_.actions.map(_.directive)), Vector(Vector("defaults"), Vector("clean"), Vector("create"), Vector("link"), Vector("shell")))
  }

  test("reads JSON5 hex numbers without preprocessing to JSON") {
    val root = os.temp.dir(prefix = "dotbot-scala-json5-")
    val path = root / "install.json5"
    os.write(
      path,
      """[
        |  { defaults: { create: { mode: 0x1ed, }, }, },
        |  { create: { generated: { mode: 0x1c0, }, }, },
        |]
        |""".stripMargin,
    )

    val tasks = ConfigReader().read(Vector(path.toString)).fold(error => fail(error), identity)
    val mode = tasks.head.actions.head.data
      .field("create")
      .flatMap(_.field("mode"))

    assertEquals(mode, Some(ConfigValue.NumberValue(BigDecimal(493))))
  }
