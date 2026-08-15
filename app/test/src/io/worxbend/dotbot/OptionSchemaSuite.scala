package io.worxbend.dotbot

import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.OptionContext
import io.worxbend.dotbot.core.OptionSchema
import io.worxbend.dotbot.core.OptionType

class OptionSchemaSuite extends munit.FunSuite:
  private val entry = OptionContext.Entry("link", "~/.vimrc")

  private def render(result: Either[io.worxbend.dotbot.core.DotbotError, Unit]): Option[String] =
    result.left.toOption.map(_.render)

  test("accepts a well-formed option map") {
    val values = Map(
      "path" -> ConfigValue.StringValue("vimrc"),
      "force" -> ConfigValue.BoolValue(true),
      "exclude" -> ConfigValue.ArrayValue(Vector(ConfigValue.StringValue("a"), ConfigValue.StringValue("b"))),
    )

    assertEquals(OptionSchema.link.validate(values, entry), Right(()))
  }

  test("rejects a YAML truthy scalar where a boolean is required") {
    // YAML 1.2 reads `yes` and `on` as strings. Silently treating them as false is how
    // `force: yes` used to become a link that quietly refused to replace anything.
    val values = Map("force" -> ConfigValue.StringValue("yes"))

    assertEquals(render(OptionSchema.link.validate(values, entry)), Some("link force for ~/.vimrc must be a boolean"))
  }

  test("rejects an unknown option and lists the ones that exist") {
    val values = Map("relnk" -> ConfigValue.BoolValue(true))
    val message = render(OptionSchema.link.validate(values, entry))

    assert(message.exists(_.startsWith("""unknown link option "relnk" for ~/.vimrc, expected one of: """)), message)
    assert(message.exists(_.contains("relink")), message)
  }

  test("reports a defaults section differently from an entry") {
    val values = Map("force" -> ConfigValue.NumberValue(BigDecimal(1)))

    assertEquals(
      render(OptionSchema.clean.validate(values, OptionContext.Defaults("clean"))),
      Some("default clean force must be a boolean"),
    )
    assertEquals(
      render(OptionSchema.clean.validate(values, OptionContext.Entry("clean", "~"))),
      Some("clean force for ~ must be a boolean"),
    )
  }

  test("reports the first offending key in a stable order") {
    // Keys are checked in sorted order so the same config always produces the same message.
    val values = Map(
      "relink" -> ConfigValue.StringValue("yes"),
      "backup" -> ConfigValue.StringValue("yes"),
    )

    assertEquals(render(OptionSchema.link.validate(values, entry)), Some("link backup for ~/.vimrc must be a boolean"))
  }

  test("path accepts a string or null, because null means derive it from the destination") {
    assertEquals(OptionSchema.link.validate(Map("path" -> ConfigValue.NullValue), entry), Right(()))
    assertEquals(
      render(OptionSchema.link.validate(Map("path" -> ConfigValue.BoolValue(true)), entry)),
      Some("link path for ~/.vimrc must be a string"),
    )
  }

  test("exclude must be a list whose every element is a string") {
    val mixed = ConfigValue.ArrayValue(Vector(ConfigValue.StringValue("ok"), ConfigValue.BoolValue(true)))

    assertEquals(
      render(OptionSchema.link.validate(Map("exclude" -> mixed), entry)),
      Some("link exclude for ~/.vimrc must be a list of strings"),
    )
  }

  test("mode accepts either spelling and rejects impossible values") {
    val create = OptionContext.Entry("create", "bin")

    assertEquals(OptionSchema.create.validate(Map("mode" -> ConfigValue.StringValue("0755")), create), Right(()))
    assertEquals(
      OptionSchema.create.validate(Map("mode" -> ConfigValue.NumberValue(BigDecimal(755))), create),
      Right(()),
    )
    assertEquals(
      render(OptionSchema.create.validate(Map("mode" -> ConfigValue.NumberValue(BigDecimal(800))), create)),
      Some("create mode for bin must be an octal string or number"),
    )
  }

  test("every directive that takes options has a schema, and defaults does not") {
    assertEquals(OptionSchema.forDirective(Directive.Link), Some(OptionSchema.link))
    assertEquals(OptionSchema.forDirective(Directive.Clean), Some(OptionSchema.clean))
    assertEquals(OptionSchema.forDirective(Directive.Create), Some(OptionSchema.create))
    assertEquals(OptionSchema.forDirective(Directive.Shell), Some(OptionSchema.shell))
    assertEquals(OptionSchema.forDirective(Directive.Defaults), None)
    assertEquals(OptionSchema.forDirective(Directive.Unknown("nope")), None)
  }

  test("the link schema covers every option LinkOptions actually reads") {
    // If someone adds an option to LinkOptions and forgets the schema, that option becomes
    // "unknown" and every config using it starts failing validation. This is the reminder.
    val expected = Set(
      "path", "type", "exclude", "prefix", "if", "relative", "canonicalize", "canonicalize-path",
      "force", "relink", "create", "glob", "backup", "ignore-missing",
    )

    assertEquals(OptionSchema.link.fields.keySet, expected)
  }

  test("option types describe themselves for error messages") {
    assertEquals(OptionType.Bool.description, "a boolean")
    assertEquals(OptionType.StrList.description, "a list of strings")
  }
