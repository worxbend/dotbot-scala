package io.worxbend.dotbot

import io.worxbend.dotbot.config.ConfigReader
import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.testkit.TestEnvironment

/**
 * Covers what happens when a configuration cannot be read or makes no sense, which is the first
 * thing a user hits when something is wrong.
 */
class ConfigReadFailureSuite extends munit.FunSuite:
  test("a missing config file is reported with the path as the user wrote it") {
    val result = ConfigReader().read(Vector("/definitely/absent/install.conf.yaml"))

    result match
      case Left(DotbotError.ConfigReadFailed(path, _)) =>
        assertEquals(path, "/definitely/absent/install.conf.yaml")
      case other => fail(s"expected a read failure, got $other")
  }

  test("an unsupported extension is rejected by name") {
    val root = os.temp.dir(prefix = "dotbot-scala-bad-ext-")
    os.write(root / "install.ini", "[]\n")

    assertEquals(
      ConfigReader().read(Vector((root / "install.ini").toString)),
      Left(DotbotError.UnsupportedConfigFormat("ini")),
    )
  }

  test("a config whose root is not a task list is rejected") {
    val root = os.temp.dir(prefix = "dotbot-scala-bad-root-")
    val path = root / "install.conf.yaml"
    os.write(path, "just a string\n")

    assertEquals(
      ConfigReader().read(Vector(path.toString)),
      Left(DotbotError.ConfigRootNotTaskList(path.toString)),
    )
  }

  test("a task that is not a mapping is reported with its position") {
    val root = os.temp.dir(prefix = "dotbot-scala-bad-task-")
    val path = root / "install.conf.yaml"
    os.write(path, "- clean: [\"~\"]\n- not-a-mapping\n")

    assertEquals(
      ConfigReader().read(Vector(path.toString)),
      Left(DotbotError.ConfigTaskNotMapping(path.toString, 2)),
    )
  }

  test("reading several files concatenates their tasks in order") {
    val root = os.temp.dir(prefix = "dotbot-scala-multi-")
    os.write(root / "first.conf.yaml", "- create: [\"one\"]\n")
    os.write(root / "second.conf.yaml", "- shell: [\"echo two\"]\n")

    val tasks = ConfigReader()
      .read(Vector((root / "first.conf.yaml").toString, (root / "second.conf.yaml").toString))
      .fold(error => fail(error.render), identity)

    assertEquals(tasks.map(_.actions.map(_.directive.label)), Vector(Vector("create"), Vector("shell")))
  }

  test("the first unreadable file stops the read") {
    val root = os.temp.dir(prefix = "dotbot-scala-multi-fail-")
    os.write(root / "ok.conf.yaml", "- create: [\"one\"]\n")

    val result = ConfigReader().read(Vector((root / "absent.conf.yaml").toString, (root / "ok.conf.yaml").toString))

    assert(result.isLeft, result)
  }

  test("a config path may itself contain a variable and a tilde") {
    val root = os.temp.dir(prefix = "dotbot-scala-path-expansion-")
    os.write(root / "install.conf.yaml", "- create: [\"one\"]\n")
    val reader = ConfigReader(TestEnvironment.resolver(Map("DOTFILES" -> root.toString)))

    val tasks = reader
      .read(Vector("$DOTFILES/install.conf.yaml"))
      .fold(error => fail(error.render), identity)

    assertEquals(tasks.map(_.actions.map(_.directive.label)), Vector(Vector("create")))
  }
