package io.worxbend.dotbot

import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DotbotError

class DotbotErrorSuite extends munit.FunSuite:
  test("renders action context errors through one format") {
    val cause = DotbotError.Decode("bad shape")

    assertEquals(DotbotError.Validation(Directive.Link, cause).render, "validating action link: bad shape")
    assertEquals(DotbotError.Execution(Directive.Shell, cause).render, "executing action shell: bad shape")
  }

  test("renders config and handler errors at the app edge") {
    assertEquals(DotbotError.ActionNotHandled(Directive.Unknown("custom")).render, "action custom not handled")
    assertEquals(
      DotbotError.ConfigReadFailed("install.conf", "missing").render,
      "could not read config file \"install.conf\": missing",
    )
    assertEquals(DotbotError.UnsupportedConfigFormat("json5").render, "unsupported config file format .json5")
    assertEquals(
      DotbotError.ConfigRootNotTaskList("install.conf").render,
      "configuration file \"install.conf\" must be a list of tasks",
    )
    assertEquals(
      DotbotError.ConfigTaskNotMapping("install.conf", 2).render,
      "configuration task 2 in \"install.conf\" must be a mapping",
    )
  }
