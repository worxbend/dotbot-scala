package io.worxbend.dotbot

import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.DecodeMode
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DirectiveDefaults

class DirectiveSuite extends munit.FunSuite:
  test("every built-in directive round-trips through its label") {
    val builtIn = Vector(Directive.Defaults, Directive.Clean, Directive.Create, Directive.Link, Directive.Shell)

    assertEquals(builtIn.map(directive => Directive.fromString(directive.label)), builtIn.map(Some(_)))
  }

  test("an unrecognized name has no typed directive") {
    assertEquals(Directive.fromString("lnik"), None)
    assertEquals(Directive.fromString(""), None)
  }

  test("parse keeps an unrecognized name instead of discarding it") {
    // Config files may legitimately contain a directive this build does not know about, and the
    // error message has to be able to name it.
    assertEquals(Directive.parse("plugin"), Directive.Unknown("plugin"))
    assertEquals(Directive.parse("plugin").label, "plugin")
  }

  test("parse and fromString agree on names that are known") {
    assertEquals(Directive.parse("link"), Directive.Link)
  }

  test("defaults sections are keyed by typed directive and unknown names are dropped") {
    val defaults = DirectiveDefaults.from(
      ConfigValue.ObjectValue(
        Vector(
          "link" -> ConfigValue.ObjectValue(Vector("create" -> ConfigValue.BoolValue(true))),
          "nonsense" -> ConfigValue.ObjectValue(Vector("x" -> ConfigValue.BoolValue(true))),
        ),
      ),
    )

    assertEquals(defaults.values.keySet, Set(Directive.Link))
    assertEquals(defaults.section(Directive.Link), Map("create" -> ConfigValue.BoolValue(true)))
  }

  test("an absent defaults section reads as empty rather than failing") {
    assertEquals(DirectiveDefaults.empty.section(Directive.Link), Map.empty)
    assertEquals(DirectiveDefaults.from(ConfigValue.NullValue).values, Map.empty)
  }

  test("a defaults section that is not a map reads as empty") {
    val defaults = DirectiveDefaults.from(
      ConfigValue.ObjectValue(Vector("link" -> ConfigValue.StringValue("nonsense"))),
    )

    assertEquals(defaults.section(Directive.Link), Map.empty)
  }

  test("only validation decoding is strict") {
    assertEquals(DecodeMode.Validate.strict, true)
    assertEquals(DecodeMode.Execute.strict, false)
  }
