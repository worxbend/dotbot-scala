package io.worxbend.dotbot

import io.worxbend.dotbot.core.CleanSpec
import io.worxbend.dotbot.core.CleanEntry
import io.worxbend.dotbot.core.ConfigDecoder
import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.CreateEntry
import io.worxbend.dotbot.core.CreateSpec
import io.worxbend.dotbot.core.DecodeMode
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DirectiveDefaults
import io.worxbend.dotbot.core.DirectiveDecoders.given
import io.worxbend.dotbot.core.FileMode
import io.worxbend.dotbot.core.LinkEntry
import io.worxbend.dotbot.core.LinkSpec
import io.worxbend.dotbot.core.LinkOptions
import io.worxbend.dotbot.core.LinkType
import io.worxbend.dotbot.core.ShellEntry
import io.worxbend.dotbot.core.ShellSpec
import io.worxbend.dotbot.core.UnknownLinkType

class DirectiveDecoderSuite extends munit.FunSuite:
  test("directive defaults parse raw labels into typed sections") {
    val defaults = DirectiveDefaults.from(
      obj(
        "create" -> obj("mode" -> str("700")),
        "unknown" -> obj("mode" -> str("600")),
      ),
    )

    assertEquals(defaults.section(Directive.Create), Map("mode" -> str("700")))
    assertEquals(defaults.section(Directive.Unknown("unknown")), Map.empty)
  }

  test("create decoder reports strict shape and mode errors") {
    assertDecodeError[CreateSpec](num(1), "create directive must be a list or map")
    assertDecodeError[CreateSpec](arr(num(1)), "create directive item must be a string")
    assertDecodeError[CreateSpec](obj("bin" -> str("bad")), "create directive options for bin must be a map")
    assertDecodeError[CreateSpec](
      obj("bin" -> obj("mode" -> str("abc"))),
      "create mode for bin must be an octal string or number",
    )
    assertDecodeError[CreateSpec](
      arr(str("bin")),
      "default create mode must be an octal string or number",
      DirectiveDefaults(Map(Directive.Create -> obj("mode" -> str("abc")))),
    )
  }

  test("create decoder merges default mode before local path options") {
    val decoded = summon[ConfigDecoder[CreateSpec]]
      .decode(
        obj(
          "bin" -> ConfigValue.NullValue,
          "cache" -> obj("mode" -> str("700")),
        ),
        DirectiveDefaults(Map(Directive.Create -> obj("mode" -> str("755")))),
        DecodeMode.Validate,
      )

    assertEquals(
      decoded,
      Right(
        CreateSpec(
          Vector(
            CreateEntry.Path("bin", mode("755")),
            CreateEntry.Path("cache", mode("700")),
          ),
        ),
      ),
    )
  }

  test("clean decoder reports strict shape errors") {
    assertDecodeError[CleanSpec](num(1), "clean directive must be a list or map")
    assertDecodeError[CleanSpec](arr(num(1)), "clean directive item must be a string")
    assertDecodeError[CleanSpec](obj("cache" -> str("bad")), "clean directive options for cache must be a map")
  }

  test("clean decoder merges defaults before local target options") {
    val decoded = summon[ConfigDecoder[CleanSpec]]
      .decode(
        obj(
          "logs" -> ConfigValue.NullValue,
          "cache" -> obj("force" -> bool(false)),
        ),
        DirectiveDefaults(
          Map(
            Directive.Clean -> obj(
              "force" -> bool(true),
              "recursive" -> bool(true),
            ),
          ),
        ),
        DecodeMode.Validate,
      )

    assertEquals(
      decoded,
      Right(
        CleanSpec(
          Vector(
            CleanEntry.Target("cache", force = false, recursive = true),
            CleanEntry.Target("logs", force = true, recursive = true),
          ),
        ),
      ),
    )
  }

  test("link decoder reports strict shape and option errors") {
    assertDecodeError[LinkSpec](arr(str("bad")), "link directive must be a map")
    assertDecodeError[LinkSpec](obj("~/.vimrc" -> bool(true)), "link target for ~/.vimrc must be a string or map")
    assertDecodeError[LinkSpec](obj("~/.vimrc" -> obj("path" -> bool(true))), "link path for ~/.vimrc must be a string")
    assertDecodeError[LinkSpec](obj("~/.vimrc" -> obj("type" -> bool(true))), "link type for ~/.vimrc must be a string")
    assertDecodeError[LinkSpec](
      obj("~/.vimrc" -> obj("exclude" -> arr(str("ok"), bool(true)))),
      "link exclude for ~/.vimrc must be a list of strings",
    )
    assertDecodeError[LinkSpec](
      obj("~/.vimrc" -> obj("path" -> str("vimrc"), "type" -> str("copy"))),
      "link type is not recognized: copy",
    )
    assertDecodeError[LinkSpec](
      obj("~/.vimrc" -> str("vimrc")),
      "default link type is not recognized: copy",
      DirectiveDefaults(Map(Directive.Link -> obj("type" -> str("copy")))),
    )
  }

  test("link decoder maps null targets to dot-stripped destination basenames") {
    val decoded = summon[ConfigDecoder[LinkSpec]]
      .decode(
        obj(
          "~/.vimrc" -> ConfigValue.NullValue,
          "~/.config/nvim" -> obj("path" -> ConfigValue.NullValue),
        ),
        DirectiveDefaults.empty,
        DecodeMode.Validate,
      )

    assertEquals(
      decoded,
      Right(
        LinkSpec(
          None,
          Vector(
            LinkEntry.Link("~/.config/nvim", "nvim", LinkOptions()),
            LinkEntry.Link("~/.vimrc", "vimrc", LinkOptions()),
          ),
        ),
      ),
    )
  }

  test("link decoder merges defaults before local link options") {
    val decoded = summon[ConfigDecoder[LinkSpec]]
      .decode(
        obj(
          "~/.tmux.conf" -> str("tmux.conf"),
          "~/.vimrc" -> obj(
            "path" -> str("vimrc"),
            "relative" -> bool(false),
            "type" -> str("symlink"),
          ),
        ),
        DirectiveDefaults(
          Map(
            Directive.Link -> obj(
              "create" -> bool(true),
              "relative" -> bool(true),
              "type" -> str("hardlink"),
            ),
          ),
        ),
        DecodeMode.Validate,
      )

    assertEquals(
      decoded,
      Right(
        LinkSpec(
          None,
          Vector(
            LinkEntry.Link(
              "~/.tmux.conf",
              "tmux.conf",
              LinkOptions(relative = true, linkType = LinkType.Hardlink, create = true),
            ),
            LinkEntry.Link(
              "~/.vimrc",
              "vimrc",
              LinkOptions(relative = false, linkType = LinkType.Symlink, create = true),
            ),
          ),
        ),
      ),
    )
  }

  test("shell decoder reports strict shape and command errors") {
    assertDecodeError[ShellSpec](str("echo hi"), "shell directive must be a list")
    assertDecodeError[ShellSpec](arr(obj("description" -> str("missing"))), "shell directive item must include a command")
    assertDecodeError[ShellSpec](arr(arr(bool(true), str("message"))), "shell directive item must include a command")
  }

  test("shell decoder merges defaults before local command options") {
    val decoded = summon[ConfigDecoder[ShellSpec]]
      .decode(
        arr(
          str("echo inherited"),
          obj(
            "command" -> str("echo local"),
            "description" -> str("Local command"),
            "quiet" -> bool(false),
            "stderr" -> bool(true),
          ),
        ),
        DirectiveDefaults(
          Map(
            Directive.Shell -> obj(
              "quiet" -> bool(true),
              "stdout" -> bool(true),
            ),
          ),
        ),
        DecodeMode.Validate,
      )

    assertEquals(
      decoded,
      Right(
        ShellSpec(
          Vector(
            ShellEntry.Command(
              command = "echo inherited",
              description = "",
              quiet = true,
              stdin = false,
              stdout = true,
              stderr = false,
            ),
            ShellEntry.Command(
              command = "echo local",
              description = "Local command",
              quiet = false,
              stdin = false,
              stdout = true,
              stderr = true,
            ),
          ),
        ),
      ),
    )
  }

  test("execute mode keeps malformed entries as soft failures") {
    assertEquals(
      summon[ConfigDecoder[CreateSpec]].decode(arr(num(1)), DirectiveDefaults.empty, DecodeMode.Execute),
      Right(CreateSpec(Vector(CreateEntry.Invalid))),
    )
    assertEquals(
      summon[ConfigDecoder[CleanSpec]].decode(arr(num(1)), DirectiveDefaults.empty, DecodeMode.Execute),
      Right(CleanSpec(Vector(CleanEntry.Invalid))),
    )
    assertEquals(
      summon[ConfigDecoder[ShellSpec]]
        .decode(arr(obj("description" -> str("missing command"))), DirectiveDefaults.empty, DecodeMode.Execute),
      Right(ShellSpec(Vector(ShellEntry.Invalid))),
    )
    assertEquals(
      summon[ConfigDecoder[LinkSpec]]
        .decode(obj("~/.vimrc" -> obj("path" -> str("vimrc"), "type" -> str("copy"))), DirectiveDefaults.empty, DecodeMode.Execute),
      Right(LinkSpec(None, Vector(LinkEntry.InvalidLinkType(UnknownLinkType("copy"))))),
    )
    assertEquals(
      summon[ConfigDecoder[LinkSpec]]
        .decode(
          obj("~/.vimrc" -> str("vimrc")),
          DirectiveDefaults(Map(Directive.Link -> obj("type" -> str("copy")))),
          DecodeMode.Execute,
        ),
      Right(LinkSpec(Some(UnknownLinkType("copy")), Vector.empty)),
    )
  }

  private def assertDecodeError[A](
    value:    ConfigValue,
    message:  String,
    defaults: DirectiveDefaults = DirectiveDefaults.empty,
  )(using decoder: ConfigDecoder[A]): Unit =
    assertEquals(decoder.decode(value, defaults, DecodeMode.Validate).left.map(_.render), Left(message))

  private def obj(fields: (String, ConfigValue)*): ConfigValue =
    ConfigValue.ObjectValue(fields.toVector)

  private def arr(items: ConfigValue*): ConfigValue =
    ConfigValue.ArrayValue(items.toVector)

  private def str(value: String): ConfigValue =
    ConfigValue.StringValue(value)

  private def bool(value: Boolean): ConfigValue =
    ConfigValue.BoolValue(value)

  private def num(value: Int): ConfigValue =
    ConfigValue.NumberValue(BigDecimal(value))

  private def mode(value: String): FileMode =
    FileMode.fromOctal(value).getOrElse(fail(s"invalid test mode $value"))
