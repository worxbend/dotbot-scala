package io.worxbend.dotbot.core

object DirectiveDecoders:
  given createSpecDecoder: ConfigDecoder[CreateSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      for
        _ <- validateDefaults(Directive.Create, defaults, mode)
        defaultMode <- decodeFileMode(
          defaults.section(Directive.Create).get("mode"),
          FileMode.rwxAll,
          "default create mode must be an octal string or number",
          mode,
        )
        spec <- ConfigDecoder.fieldsOrList(data, "create directive must be a list or map")(
          fields => decodeCreateMap(fields, defaultMode, mode),
          paths => decodeCreateArray(paths, defaultMode, mode),
        )
      yield spec
    }

  given cleanSpecDecoder: ConfigDecoder[CleanSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      val cleanDefaults = defaults.section(Directive.Clean)
      for
        _ <- validateDefaults(Directive.Clean, defaults, mode)
        spec <- ConfigDecoder.fieldsOrList(data, "clean directive must be a list or map")(
          fields => decodeCleanMap(fields, cleanDefaults, mode),
          items => decodeCleanArray(items, cleanDefaults, mode),
        )
      yield spec
    }

  given linkSpecDecoder: ConfigDecoder[LinkSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      for
        _ <- validateDefaults(Directive.Link, defaults, mode)
        links <- ConfigDecoder.asFields(data, "link directive must be a map")
        spec <- decodeLinkSpec(links, defaults.section(Directive.Link), mode)
      yield spec
    }

  given shellSpecDecoder: ConfigDecoder[ShellSpec] =
    ConfigDecoder.instance { (data, defaults, mode) =>
      for
        _ <- validateDefaults(Directive.Shell, defaults, mode)
        items <- ConfigDecoder.asArray(data, "shell directive must be a list")
        spec <- decodeShellItems(items, defaults.section(Directive.Shell), mode)
      yield spec
    }

  /**
   * Check a `defaults` section against the schema of the directive it configures.
   *
   * A `defaults` block silently ignoring a misspelled key is worse than a mistyped entry option,
   * because one bad key changes the behavior of every later directive in the file.
   */
  private def validateDefaults(
    directive: Directive,
    defaults:  DirectiveDefaults,
    mode:      DecodeMode,
  ): Either[DotbotError, Unit] =
    if !mode.strict then Right(())
    else
      OptionSchema
        .forDirective(directive)
        .fold(Right(()))(_.validate(defaults.section(directive), OptionContext.Defaults(directive.label)))

  private def decodeLinkSpec(
    links:         ConfigFields,
    linkDefaults:  Map[String, ConfigValue],
    mode:          DecodeMode,
  ): Either[DotbotError, LinkSpec] =
    LinkOptions.fromDefaults(linkDefaults) match
      case Left(linkType) if mode.strict =>
        ConfigDecoder.fail(s"default link type is not recognized: ${linkType.render}")
      case Left(linkType) => Right(LinkSpec(Some(linkType), Vector.empty))
      case Right(defaultOptions) =>
        decodeLinkEntries(links, defaultOptions, mode).map(entries => LinkSpec(None, entries))

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
            case other                         => CreateEntry.Invalid(other.describe)
          },
        ),
      )

  private def decodeCreateMap(
    fields:      ConfigFields,
    defaultMode: FileMode,
    mode:        DecodeMode,
  ): Either[DotbotError, CreateSpec] =
    val bad =
      if !mode.strict then None
      else
        fields.entries.collectFirst {
          case (path, value) if value != ConfigValue.NullValue && value.asMap.isEmpty =>
            s"create directive options for $path must be a map"
        }

    bad match
      case Some(error) => ConfigDecoder.fail(error)
      case None =>
        EitherUtil.traverse(fields.keys) { path =>
          val local = fields(path).asMap.getOrElse(Map.empty)
          for
            _ <- validateEntry(OptionSchema.create, local, OptionContext.Entry(Directive.Create.label, path), mode)
            entryMode <- decodeFileMode(
              local.get("mode"),
              defaultMode,
              s"create mode for $path must be an octal string or number",
              mode,
            )
          yield CreateEntry.Path(path, entryMode)
        }.map(entries => CreateSpec(entries))

  private def decodeCleanArray(
    items:         Vector[ConfigValue],
    cleanDefaults: Map[String, ConfigValue],
    mode:          DecodeMode,
  ): Either[DotbotError, CleanSpec] =
    val defaultForce = cleanDefaults.boolValue("force", false)
    val defaultRecursive = cleanDefaults.boolValue("recursive", false)
    if mode.strict && items.exists(_.asString.isEmpty) then ConfigDecoder.fail("clean directive item must be a string")
    else
      Right(
        CleanSpec(
          items.map {
            case ConfigValue.StringValue(target) => CleanEntry.Target(target, defaultForce, defaultRecursive)
            case other                           => CleanEntry.Invalid(other.describe)
          },
        ),
      )

  private def decodeCleanMap(
    fields:        ConfigFields,
    cleanDefaults: Map[String, ConfigValue],
    mode:          DecodeMode,
  ): Either[DotbotError, CleanSpec] =
    val defaultForce = cleanDefaults.boolValue("force", false)
    val defaultRecursive = cleanDefaults.boolValue("recursive", false)
    val bad =
      if !mode.strict then None
      else
        fields.entries.collectFirst {
          case (target, value) if value != ConfigValue.NullValue && value.asMap.isEmpty =>
            s"clean directive options for $target must be a map"
        }

    bad match
      case Some(error) => ConfigDecoder.fail(error)
      case None =>
        EitherUtil.traverse(fields.keys) { target =>
          val local = fields(target).asMap.getOrElse(Map.empty)
          validateEntry(OptionSchema.clean, local, OptionContext.Entry(Directive.Clean.label, target), mode).map { _ =>
            CleanEntry.Target(
              target,
              local.boolValue("force", defaultForce),
              local.boolValue("recursive", defaultRecursive),
            )
          }
        }.map(entries => CleanSpec(entries))

  private def validateLinkMap(linkName: String, values: Map[String, ConfigValue]): Either[DotbotError, Unit] =
    OptionSchema.link.validate(values, OptionContext.Entry(Directive.Link.label, linkName))

  /** Check one entry's option map against its directive schema, but only in strict mode. */
  private def validateEntry(
    schema:  OptionSchema,
    values:  Map[String, ConfigValue],
    context: OptionContext,
    mode:    DecodeMode,
  ): Either[DotbotError, Unit] =
    if mode.strict then schema.validate(values, context) else Right(())

  private def decodeLinkEntries(
    links:          ConfigFields,
    defaultOptions: LinkOptions,
    mode:           DecodeMode,
  ): Either[DotbotError, Vector[LinkEntry]] =
    EitherUtil.traverse(links.keys)(linkName => decodeLinkEntry(linkName, links(linkName), defaultOptions, mode))

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
      EitherUtil.traverse(items)(decodeShellItem(_, defaults, mode)).map(entries => ShellSpec(entries))

  private def decodeShellItem(
    item:     ConfigValue,
    defaults: Map[String, ConfigValue],
    mode:     DecodeMode,
  ): Either[DotbotError, ShellEntry] =
    shellCommandSpec(item) match
      case None => Right(ShellEntry.Invalid(item.describe))
      case Some((command, message)) =>
        // Only a map form carries options; the string and [command, description] list forms have
        // no keys to check.
        val local = item.asMap.getOrElse(Map.empty)
        validateEntry(OptionSchema.shell, local, OptionContext.Entry(Directive.Shell.label, command), mode).map { _ =>
          ShellEntry.Command(
            command = command,
            description = message,
            quiet = local.boolValue("quiet", defaults.boolValue("quiet", false)),
            stdin = local.boolValue("stdin", defaults.boolValue("stdin", false)),
            stdout = local.boolValue("stdout", defaults.boolValue("stdout", false)),
            stderr = local.boolValue("stderr", defaults.boolValue("stderr", false)),
          )
        }

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
