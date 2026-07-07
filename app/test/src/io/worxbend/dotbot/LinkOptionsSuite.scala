package io.worxbend.dotbot

import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.LinkOptions
import io.worxbend.dotbot.core.LinkType
import io.worxbend.dotbot.core.UnknownLinkType

class LinkOptionsSuite extends munit.FunSuite:
  test("link type parses only supported values") {
    assertEquals(LinkType.fromString("symlink"), Some(LinkType.Symlink))
    assertEquals(LinkType.fromString("hardlink"), Some(LinkType.Hardlink))
    assertEquals(LinkType.fromString("copy"), None)
  }

  test("merge applies all configured options over the fallback") {
    val fallback = LinkOptions(relative = true, exclude = Vector("old"))
    val merged = LinkOptions.merge(
      fallback,
      Map(
        "relative" -> bool(false),
        "canonicalize" -> bool(false),
        "type" -> str("hardlink"),
        "force" -> bool(true),
        "relink" -> bool(true),
        "create" -> bool(true),
        "glob" -> bool(true),
        "backup" -> bool(true),
        "prefix" -> str("dot-"),
        "if" -> str("test -f source"),
        "ignore-missing" -> bool(true),
        "exclude" -> arr(str("skip-a"), str("skip-b")),
      ),
    )

    assertEquals(
      merged,
      Right(
        LinkOptions(
          relative = false,
          canonicalize = false,
          linkType = LinkType.Hardlink,
          force = true,
          relink = true,
          create = true,
          glob = true,
          backup = true,
          prefix = "dot-",
          ifCommand = "test -f source",
          ignoreMissing = true,
          exclude = Vector("skip-a", "skip-b"),
        ),
      ),
    )
  }

  test("canonicalize-path alias is used when canonicalize is absent") {
    assertEquals(LinkOptions.merge(LinkOptions(), Map("canonicalize-path" -> bool(false))).map(_.canonicalize), Right(false))
  }

  test("canonicalize has precedence over canonicalize-path") {
    val merged = LinkOptions.merge(
      LinkOptions(),
      Map("canonicalize" -> bool(true), "canonicalize-path" -> bool(false)),
    )

    assertEquals(merged.map(_.canonicalize), Right(true))
  }

  test("missing options preserve fallback values") {
    val fallback = LinkOptions(relative = true, linkType = LinkType.Hardlink, prefix = "existing-", exclude = Vector("old"))

    assertEquals(LinkOptions.merge(fallback, Map.empty), Right(fallback))
  }

  test("permissive string list parsing is local to link option compatibility") {
    val fallback = LinkOptions(prefix = "existing-", ifCommand = "true", exclude = Vector("old"))
    val merged = LinkOptions.merge(
      fallback,
      Map(
        "prefix" -> bool(true),
        "if" -> bool(false),
        "exclude" -> arr(str("keep"), bool(true)),
      ),
    )

    assertEquals(merged.map(options => (options.prefix, options.ifCommand, options.exclude)), Right(("existing-", "true", Vector("keep"))))
  }

  test("invalid link types are returned as typed parse errors") {
    assertEquals(LinkOptions.merge(LinkOptions(), Map("type" -> str("copy"))), Left(UnknownLinkType("copy")))
  }

  private def str(value: String): ConfigValue =
    ConfigValue.StringValue(value)

  private def bool(value: Boolean): ConfigValue =
    ConfigValue.BoolValue(value)

  private def arr(items: ConfigValue*): ConfigValue =
    ConfigValue.ArrayValue(items.toVector)
