package io.worxbend.dotbot

import io.worxbend.dotbot.config.ConfigParsers

/**
 * A Dotbot configuration is an ordered program: `defaults` applies only to the directives written
 * after it, and links are made in the order given. These tests pin that order for every supported
 * format, including several directives inside a single task — which is where HOCON and JSON used
 * to shuffle them into hash order.
 */
class ConfigOrderSuite extends munit.FunSuite:
  private def directivesOf(extension: String, text: String): Vector[Vector[String]] =
    ConfigParsers
      .parseTasks(s"install.$extension", extension, text)
      .fold(error => fail(error.render), identity)
      .map(_.actions.map(_.directive.label))

  private val expected = Vector(Vector("defaults", "create", "link", "shell", "clean"))

  test("YAML keeps the order of several directives inside one task") {
    assertEquals(
      directivesOf(
        "yaml",
        """- defaults:
          |    link:
          |      create: true
          |  create:
          |    - generated
          |  link:
          |    linked.txt: source.txt
          |  shell:
          |    - echo ok
          |  clean:
          |    - "~"
          |""".stripMargin,
      ),
      expected,
    )
  }

  test("HOCON keeps the order of several directives inside one task") {
    assertEquals(
      directivesOf(
        "conf",
        """tasks = [
          |  {
          |    defaults = { link = { create = true } }
          |    create = ["generated"]
          |    link = { "linked.txt" = "source.txt" }
          |    shell = ["echo ok"]
          |    clean = ["~"]
          |  }
          |]
          |""".stripMargin,
      ),
      expected,
    )
  }

  test("JSON keeps the order of several directives inside one task") {
    assertEquals(
      directivesOf(
        "json",
        """[
          |  {
          |    "defaults": {"link": {"create": true}},
          |    "create": ["generated"],
          |    "link": {"linked.txt": "source.txt"},
          |    "shell": ["echo ok"],
          |    "clean": ["~"]
          |  }
          |]
          |""".stripMargin,
      ),
      expected,
    )
  }

  test("TOML keeps the order of several directives inside one task") {
    assertEquals(
      directivesOf(
        "toml",
        """[[tasks]]
          |defaults = { link = { create = true } }
          |create = ["generated"]
          |link = { "linked.txt" = "source.txt" }
          |shell = ["echo ok"]
          |clean = ["~"]
          |""".stripMargin,
      ),
      expected,
    )
  }

  test("a defaults block reaches the directives written after it in the same HOCON task") {
    // This is what the ordering is actually for: if `defaults` were reordered to the end, the
    // link below would silently run without `create`.
    val tasks = ConfigParsers
      .parseTasks(
        "install.conf",
        "conf",
        """tasks = [
          |  {
          |    defaults = { link = { create = true } }
          |    link = { "nested/linked.txt" = "source.txt" }
          |  }
          |]
          |""".stripMargin,
      )
      .fold(error => fail(error.render), identity)

    assertEquals(tasks.head.actions.head.directive.label, "defaults")
  }

  test("nested option keys keep their order too") {
    val tasks = ConfigParsers
      .parseTasks(
        "install.conf",
        "conf",
        """tasks = [
          |  {
          |    link = {
          |      "a.txt" = "a-source"
          |      "b.txt" = "b-source"
          |      "c.txt" = "c-source"
          |    }
          |  }
          |]
          |""".stripMargin,
      )
      .fold(error => fail(error.render), identity)

    val links = tasks.head.actions.head.data.asObject.map(_.map(_._1))
    assertEquals(links, Some(Vector("a.txt", "b.txt", "c.txt")))
  }
