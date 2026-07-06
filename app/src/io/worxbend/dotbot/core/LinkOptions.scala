package io.worxbend.dotbot.core

import io.worxbend.dotbot.config.ConfigValue

final case class LinkOptions(
  relative:      Boolean = false,
  canonicalize:  Boolean = true,
  linkType:      String = "symlink",
  force:         Boolean = false,
  relink:        Boolean = false,
  create:        Boolean = false,
  glob:          Boolean = false,
  backup:        Boolean = false,
  prefix:        String = "",
  ifCommand:     String = "",
  ignoreMissing: Boolean = false,
  exclude:       Vector[String] = Vector.empty,
)

object LinkOptions:
  def fromDefaults(defaults: Map[String, ConfigValue]): LinkOptions =
    merge(LinkOptions(), defaults)

  def merge(options: LinkOptions, values: Map[String, ConfigValue]): LinkOptions =
    val canonical =
      values.get("canonicalize").flatMap(_.asBoolean)
        .orElse(values.get("canonicalize-path").flatMap(_.asBoolean))
        .getOrElse(options.canonicalize)

    options.copy(
      relative = Values.boolValue(values, "relative", options.relative),
      canonicalize = canonical,
      linkType = Values.stringValue(values, "type", options.linkType),
      force = Values.boolValue(values, "force", options.force),
      relink = Values.boolValue(values, "relink", options.relink),
      create = Values.boolValue(values, "create", options.create),
      glob = Values.boolValue(values, "glob", options.glob),
      backup = Values.boolValue(values, "backup", options.backup),
      prefix = Values.stringValue(values, "prefix", options.prefix),
      ifCommand = Values.stringValue(values, "if", options.ifCommand),
      ignoreMissing = Values.boolValue(values, "ignore-missing", options.ignoreMissing),
      exclude = values.get("exclude").map(Values.stringSlice).getOrElse(options.exclude),
    )

  def validLinkType(value: String): Boolean =
    value == "symlink" || value == "hardlink"
