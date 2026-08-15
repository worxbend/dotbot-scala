package io.worxbend.dotbot

import io.worxbend.dotbot.core.PathUtil
import io.worxbend.dotbot.testkit.TestEnvironment

/**
 * Covers the pure path algebra in `PathUtil`. Everything that depends on the environment lives in
 * `PathResolver` and is covered by `PathResolverSuite`.
 */
class PathUtilSuite extends munit.FunSuite:
  test("normalizes traversal segments out of joined paths") {
    assertEquals(PathUtil.join("/tmp/dotbot", "a/../b"), "/tmp/dotbot/b")
    assertEquals(PathUtil.clean("/tmp/dotbot/../dotbot/file"), "/tmp/dotbot/file")
  }

  test("computes dirname basename and relative paths") {
    assertEquals(PathUtil.dirname("/tmp/dotbot/file.txt"), "/tmp/dotbot")
    assertEquals(PathUtil.basename("/tmp/dotbot/file.txt"), "file.txt")
    assertEquals(PathUtil.relative("/tmp/dotbot", "/tmp/dotbot/nested/file.txt"), "nested/file.txt")
  }

  test("dirname of a bare filename is the current directory") {
    assertEquals(PathUtil.dirname("file.txt"), ".")
    assertEquals(PathUtil.basename("file.txt"), "file.txt")
  }

  test("relative falls back to the target when the two paths cannot be related") {
    // A relative path cannot be expressed as an offset from an absolute one.
    assertEquals(PathUtil.relative("/tmp/dotbot", "relative/target"), "relative/target")
  }
