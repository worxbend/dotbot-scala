package io.worxbend.dotbot.core

object DirectiveDecoders:
  given createSpecDecoder: ConfigDecoder[CreateSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      decodeFileMode(
        defaults.section(Directive.Create).get("mode"),
        FileMode.rwxAll,
        "default create mode must be an octal string or number",
        mode,
      ).flatMap { defaultMode =>
        ConfigDecoder.mapOrList(data, "create directive must be a list or map")(
          map => decodeCreateMap(map, defaultMode, mode),
          paths => decodeCreateArray(paths, defaultMode, mode),
        )
      }
    }

  given cleanSpecDecoder: ConfigDecoder[CleanSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      val cleanDefaults = defaults.section(Directive.Clean)
      val defaultForce = cleanDefaults.boolValue("force", false)
      val defaultRecursive = cleanDefaults.boolValue("recursive", false)

      ConfigDecoder.mapOrList(data, "clean directive must be a list or map")(
        map => decodeCleanMap(map, defaultForce, defaultRecursive, mode),
        items => decodeCleanArray(items, defaultForce, defaultRecursive, mode),
      )
    }

  given linkSpecDecoder: ConfigDecoder[LinkSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      ConfigDecoder.asMap(data, "link directive must be a map").flatMap { links =>
        val linkDefaults = defaults.section(Directive.Link)
        LinkOptions.fromDefaults(linkDefaults) match
          case Left(linkType) if mode.strict =>
            ConfigDecoder.fail(s"default link type is not recognized: ${linkType.render}")
          case Left(linkType) => Right(LinkSpec(Some(linkType), Vector.empty))
          case Right(defaultOptions) =>
            decodeLinkEntries(links, defaultOptions, mode).map(entries => LinkSpec(None, entries))
      }
    }

  given shellSpecDecoder: ConfigDecoder[ShellSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      ConfigDecoder
        .asArray(data, "shell directive must be a list")
        .flatMap(items => decodeShellItems(items, defaults.section(Directive.Shell), mode))
    }

  private def decodeCreateArray(
    paths:       Vector[ConfigValue],
    defaultMode: FileMode,
    mode:        DecodeMode,
  ): Either[DotbotError, CreateSpec] =
    if mode.strict && paths.exists(_.asString.isEmpty) then ConfigDecoder.fail("create directive item must be a string")
    else
      Right(
        CreateSpec(
          paths.map {
            case ConfigValue.StringValue(path) => CreateEntry.Path(path, defaultMode)
            case _                            => CreateEntry.Invalid
          },
        ),
      )

  private def decodeCreateMap(
    map:         Map[String, ConfigValue],
    defaultMode: FileMode,
    mode:        DecodeMode,
  ): Either[DotbotError, CreateSpec] =
    val bad =
      if !mode.strict then None
      else
        map.collectFirst {
          case (path, value) if value != ConfigValue.NullValue && value.asMap.isEmpty =>
            s"create directive options for $path must be a map"
        }

    bad match
      case Some(error) => ConfigDecoder.fail(error)
      case None =>
        EitherUtil.traverse(map.sortedKeys) { path =>
          val local = map(path).asMap
          val entryMode =
            local match
              case Some(values) =>
                decodeFileMode(values.get("mode"), defaultMode, s"create mode for $path must be an octal string or number", mode)
              case None => Right(defaultMode)
          entryMode.map(CreateEntry.Path(path, _))
        }.map(entries => CreateSpec(entries))

  private def decodeCleanArray(
    items:            Vector[ConfigValue],
    defaultForce:     Boolean,
    defaultRecursive: Boolean,
    mode:             DecodeMode,
  ): Either[DotbotError, CleanSpec] =
    if mode.strict && items.exists(_.asString.isEmpty) then ConfigDecoder.fail("clean directive item must be a string")
    else
      Right(
        CleanSpec(
          items.map {
            case ConfigValue.StringValue(target) => CleanEntry.Target(target, defaultForce, defaultRecursive)
            case _                               => CleanEntry.Invalid
          },
        ),
      )

  private def decodeCleanMap(
    map:              Map[String, ConfigValue],
    defaultForce:     Boolean,
    defaultRecursive: Boolean,
    mode:             DecodeMode,
  ): Either[DotbotError, CleanSpec] =
    val bad =
      if !mode.strict then None
      else
        map.collectFirst {
          case (target, value) if value != ConfigValue.NullValue && value.asMap.isEmpty =>
            s"clean directive options for $target must be a map"
        }

    bad match
      case Some(error) => ConfigDecoder.fail(error)
      case None =>
        Right(
          CleanSpec(
            map.sortedKeys.map { target =>
              val local = map(target).asMap.getOrElse(Map.empty)
              CleanEntry.Target(
                target,
                local.boolValue("force", defaultForce),
                local.boolValue("recursive", defaultRecursive),
              )
            },
          ),
        )

  private def validateLinkMap(linkName: String, values: Map[String, ConfigValue]): Either[DotbotError, Unit] =
    def validateOptional(name: String)(valid: ConfigValue => Boolean, error: String): Either[DotbotError, Unit] =
      ConfigDecoder.optional(values, name) { value =>
        if valid(value) then Right(())
        else ConfigDecoder.fail(error)
      }.map(_ => ())

    for
      _ <- validateOptional("path")(value => value == ConfigValue.NullValue || value.asString.nonEmpty, s"link path for $linkName must be a string")
      _ <- validateOptional("type")(_.asString.nonEmpty, s"link type for $linkName must be a string")
      _ <- validateOptional("exclude")(_.isStringList, s"link exclude for $linkName must be a list of strings")
    yield ()

  private def decodeLinkEntries(
    links:          Map[String, ConfigValue],
    defaultOptions: LinkOptions,
    mode:           DecodeMode,
  ): Either[DotbotError, Vector[LinkEntry]] =
    EitherUtil.traverse(links.sortedKeys)(linkName => decodeLinkEntry(linkName, links(linkName), defaultOptions, mode))

  private def decodeLinkEntry(
    linkName:       String,
    target:         ConfigValue,
    defaultOptions: LinkOptions,
    mode:           DecodeMode,
  ): Either[DotbotError, LinkEntry] =
    target.asMap match
      case Some(targetMap) =>
        if mode.strict then
          validateLinkMap(linkName, targetMap).flatMap { _ =>
            LinkOptions.merge(defaultOptions, targetMap)
              .left.map(linkType => ConfigDecoder.decodeError(s"link type is not recognized: ${linkType.render}"))
              .map { options =>
                LinkEntry.Link(linkName, defaultTarget(linkName, targetMap.getOrElse("path", ConfigValue.NullValue)), options)
              }
          }
        else
          LinkOptions.merge(defaultOptions, targetMap) match
            case Left(linkType) => Right(LinkEntry.InvalidLinkType(linkType))
            case Right(options) =>
              Right(LinkEntry.Link(linkName, defaultTarget(linkName, targetMap.getOrElse("path", ConfigValue.NullValue)), options))
      case None =>
        if mode.strict && target != ConfigValue.NullValue && target.asString.isEmpty then
          ConfigDecoder.fail(s"link target for $linkName must be a string or map")
        else Right(LinkEntry.Link(linkName, defaultTarget(linkName, target), defaultOptions))

  private def defaultTarget(linkName: String, target: ConfigValue): String =
    target match
      case ConfigValue.NullValue =>
        val base = PathUtil.basename(linkName)
        if base.startsWith(".") then base.drop(1) else base
      case _ => target.asString.getOrElse("")

  private def decodeFileMode(
    value:    Option[ConfigValue],
    fallback: FileMode,
    message:  String,
    mode:     DecodeMode,
  ): Either[DotbotError, FileMode] =
    if mode.strict then FileMode.decodeConfig(value, fallback, message)
    else Right(FileMode.fromConfig(value, fallback))

  private def decodeShellItems(
    items:    Vector[ConfigValue],
    defaults: Map[String, ConfigValue],
    mode:     DecodeMode,
  ): Either[DotbotError, ShellSpec] =
    if mode.strict && items.exists(shellCommandSpec(_).isEmpty) then ConfigDecoder.fail("shell directive item must include a command")
    else
      Right(
        ShellSpec(
          items.map { item =>
            shellCommandSpec(item) match
              case None => ShellEntry.Invalid
              case Some((command, message)) =>
                val local = item.asMap.getOrElse(Map.empty)
                ShellEntry.Command(
                  command = command,
                  description = message,
                  quiet = local.boolValue("quiet", defaults.boolValue("quiet", false)),
                  stdin = local.boolValue("stdin", defaults.boolValue("stdin", false)),
                  stdout = local.boolValue("stdout", defaults.boolValue("stdout", false)),
                  stderr = local.boolValue("stderr", defaults.boolValue("stderr", false)),
                )
            },
        ),
      )

  private def shellCommandSpec(item: ConfigValue): Option[(String, String)] =
    ConfigDecoder.oneOf(item)(
      _.asString.map(_ -> ""),
      shellMapCommandSpec,
      _.asArray.flatMap { list =>
        list.headOption.flatMap(_.asString).map { command =>
          command -> list.drop(1).headOption.flatMap(_.asString).getOrElse("")
        }
      },
    )

  private def shellMapCommandSpec(item: ConfigValue): Option[(String, String)] =
    item.asMap.flatMap { map =>
      ConfigDecoder
        .field(map, "command", "shell directive item must include a command") { value =>
          value.asString.toRight(ConfigDecoder.decodeError("shell directive item must include a command"))
        }
        .toOption
        .map { command =>
          command -> map.get("description").flatMap(_.asString).getOrElse("")
        }
    }
