package io.worxbend.dotbot

import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DirectiveRegistry

class WiringSuite extends munit.FunSuite:
  test("directive registry contains all built-in handlers") {
    val handlers = DirectiveRegistry.handlers
    val directiveKeys = Set(Directive.Clean, Directive.Create, Directive.Link, Directive.Shell)
    assertEquals(handlers.keySet, directiveKeys)
    assertEquals(handlers.values.toSet.map(_.directive), directiveKeys)
  }
