package io.worxbend.dotbot

import io.worxbend.dotbot.config.ConfigParsers
import io.worxbend.dotbot.core.DotbotError

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

  test("an array of single-directive tasks keeps its order even written on one line") {
    // The task list is a HOCON array, and an array is an ordered list rather than a map, so this
    // is exact regardless of layout. It is the reason one directive per element is the form to use.
    assertEquals(
      directivesOf(
        "conf",
        """tasks = [ {defaults={link={create=true}}}, {create=["a"]}, {link={x=y}}, """ +
          """{shell=["echo"]}, {clean=["~"]} ]""",
      ),
      Vector(Vector("defaults"), Vector("create"), Vector("link"), Vector("shell"), Vector("clean")),
    )
  }

  test("JSON keeps its order when a task is written entirely on one line") {
    // jsoniter reads fields in stream order, so JSON needs no line-number recovery at all.
    assertEquals(
      directivesOf("json", """[{"defaults": {}, "create": ["a"], "link": {}, "shell": [], "clean": []}]"""),
      Vector(Vector("defaults", "create", "link", "shell", "clean")),
    )
  }

  test("a HOCON task with several directives on one line is rejected, not guessed at") {
    // HOCON records only the line a value came from, so two directives sharing a line cannot be
    // ordered. Falling back to hash order is exactly the silent misbehavior being prevented.
    val result = ConfigParsers.parseTasks(
      "install.conf",
      "conf",
      """tasks = [
        |  { defaults = { link = { create = true } }, create = ["a"] }
        |]
        |""".stripMargin,
    )

    result match
      case Left(DotbotError.AmbiguousTaskOrder(path, line, directives)) =>
        assertEquals(path, "install.conf")
        assertEquals(line, 2)
        assertEquals(directives, Vector("create", "defaults"))
      case other => fail(s"expected an ambiguous-order error, got $other")
  }

  test("the ambiguity error tells the user how to fix it") {
    val rendered = ConfigParsers
      .parseTasks("install.conf", "conf", "tasks = [ { create = [\"a\"], clean = [\"~\"] } ]")
      .left
      .map(_.render)

    assert(rendered.left.exists(_.contains("put each directive on its own line")), rendered)
  }

  test("a single-directive task on one line is fine, having nothing to order") {
    assertEquals(
      directivesOf("conf", """tasks = [ { create = ["a"] }, { clean = ["~"] } ]"""),
      Vector(Vector("create"), Vector("clean")),
    )
  }

  test("directives on separate lines are still accepted and ordered") {
    assertEquals(
      directivesOf(
        "conf",
        """tasks = [
          |  {
          |    defaults = { link = { create = true } }
          |    create = ["a"]
          |  }
          |]
          |""".stripMargin,
      ),
      Vector(Vector("defaults", "create")),
    )
  }
