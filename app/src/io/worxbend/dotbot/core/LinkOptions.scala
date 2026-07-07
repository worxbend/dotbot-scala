package io.worxbend.dotbot.core

enum LinkType(val label: String):
  case Symlink extends LinkType("symlink")
  case Hardlink extends LinkType("hardlink")

object LinkType:
  def fromString(value: String): Option[LinkType] =
    value match
      case "symlink"  => Some(LinkType.Symlink)
      case "hardlink" => Some(LinkType.Hardlink)
      case _          => None

final case class UnknownLinkType(value: String):
  def render: String = value

final case class LinkOptions(
  relative:      Boolean = false,
  canonicalize:  Boolean = true,
  linkType:      LinkType = LinkType.Symlink,
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
  def fromDefaults(defaults: Map[String, ConfigValue]): Either[UnknownLinkType, LinkOptions] =
    merge(LinkOptions(), defaults)

  def merge(options: LinkOptions, values: Map[String, ConfigValue]): Either[UnknownLinkType, LinkOptions] =
    val canonical =
      values.get("canonicalize").flatMap(_.asBoolean)
        .orElse(values.get("canonicalize-path").flatMap(_.asBoolean))
        .getOrElse(options.canonicalize)

    linkType(values, options.linkType).map { resolvedLinkType =>
      options.copy(
        relative = values.boolValue("relative", options.relative),
        canonicalize = canonical,
        linkType = resolvedLinkType,
        force = values.boolValue("force", options.force),
        relink = values.boolValue("relink", options.relink),
        create = values.boolValue("create", options.create),
        glob = values.boolValue("glob", options.glob),
        backup = values.boolValue("backup", options.backup),
        prefix = optionalString(values, "prefix", options.prefix),
        ifCommand = optionalString(values, "if", options.ifCommand),
        ignoreMissing = values.boolValue("ignore-missing", options.ignoreMissing),
        exclude = values.get("exclude").map(permissiveStringList).getOrElse(options.exclude),
      )
    }

  private def linkType(values: Map[String, ConfigValue], fallback: LinkType): Either[UnknownLinkType, LinkType] =
    values.get("type").flatMap(_.asString) match
      case None        => Right(fallback)
      case Some(value) => LinkType.fromString(value).toRight(UnknownLinkType(value))

  private def optionalString(values: Map[String, ConfigValue], key: String, fallback: String): String =
    values.get(key).flatMap(_.asString).getOrElse(fallback)

  private def permissiveStringList(value: ConfigValue): Vector[String] =
    value.asArray.toVector.flatten.flatMap(_.asString)
