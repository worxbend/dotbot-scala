package io.worxbend.dotbot

import io.worxbend.dotbot.core.PathUtil

class PathUtilSuite extends munit.FunSuite:
  test("expands user home") {
    assertEquals(PathUtil.path("~"), os.home.toString)
    assertEquals(PathUtil.path("~/dotbot-scala-test"), (os.home / "dotbot-scala-test").toString)
  }

  test("replaces undefined environment variables with empty strings") {
    assertEquals(PathUtil.path("before-$DOTBOT_SCALA_UNDEFINED_TEST_VAR-after"), "before--after")
    assertEquals(PathUtil.path("before-${DOTBOT_SCALA_UNDEFINED_TEST_VAR}-after"), "before--after")
  }

  test("normalizes absolute and base-relative paths") {
    assertEquals(PathUtil.absFrom("/tmp/dotbot", "a/../b"), "/tmp/dotbot/b")
    assertEquals(PathUtil.clean("/tmp/dotbot/../dotbot/file"), "/tmp/dotbot/file")
  }

  test("computes dirname basename and relative paths") {
    assertEquals(PathUtil.dirname("/tmp/dotbot/file.txt"), "/tmp/dotbot")
    assertEquals(PathUtil.basename("/tmp/dotbot/file.txt"), "file.txt")
    assertEquals(PathUtil.relative("/tmp/dotbot", "/tmp/dotbot/nested/file.txt"), "nested/file.txt")
  }
