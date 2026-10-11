package io.worxbend.dotbot.core

/**
 * The kind of value a directive option accepts.
 */
enum OptionType(val description: String):
  case Bool      extends OptionType("a boolean")
  case Str       extends OptionType("a string")
  case StrList   extends OptionType("a list of strings")
  case StrOrNull extends OptionType("a string")
  case Mode      extends OptionType("an octal string or number")

  def accepts(value: ConfigValue): Boolean =
    this match
      case Bool      => value.asBoolean.isDefined
      case Str       => value.asString.isDefined
      case StrList   => value.isStringList
      case StrOrNull => value == ConfigValue.NullValue || value.asString.isDefined
      case Mode      =>
        value match
          case ConfigValue.StringValue(item) => FileMode.fromOctal(item).isDefined
          case ConfigValue.NumberValue(item) => FileMode.fromNumber(item).isDefined
          case _                             => false

/**
 * Where an option was written, which decides how a problem with it is reported.
 */
enum OptionContext:
  /** An option on a single directive entry, such as one link destination. */
  case Entry(directive: String, name: String)

  /** An option in a `defaults` section, which applies to every later directive of that kind. */
  case Defaults(directive: String)

  def mismatch(key: String, optionType: OptionType): String =
    this match
      case Entry(directive, name) => s"$directive $key for $name must be ${optionType.description}"
      case Defaults(directive)    => s"default $directive $key must be ${optionType.description}"

  def unknown(key: String, known: Vector[String]): String =
    val where =
      this match
        case Entry(directive, name) => s"unknown $directive option \"$key\" for $name"
        case Defaults(directive)    => s"unknown default $directive option \"$key\""
    s"$where, expected one of: ${known.mkString(", ")}"

/**
 * The options one directive understands, used to check a configuration before anything runs.
 *
 * This exists because `validate` used to accept almost anything. Option values were read with a
 * "use it if it is the right type, otherwise silently fall back to the default" helper, so
 * `force: yes` — which YAML reads as the *string* "yes", not a boolean — quietly became `false`,
 * and a misspelled key such as `relnk` was dropped entirely. In both cases `validate` reported a
 * valid configuration and the link then failed to do what the user had asked for.
 *
 * Checking against a declared schema turns both of those into an error naming the offending key,
 * which is the whole point of having a `validate` mode.
 */
final case class OptionSchema(fields: Map[String, OptionType]):
  def validate(values: Map[String, ConfigValue], context: OptionContext): Either[DotbotError, Unit] =
    EitherUtil
      .traverse(values.sortedKeys)(key => validateField(key, values(key), context))
      .map(_ => ())

  private def validateField(
      key: String,
      value: ConfigValue,
      context: OptionContext,
  ): Either[DotbotError, Unit] =
    fields.get(key) match
      case None                                          => Left(DotbotError.Decode(context.unknown(key, knownKeys)))
      case Some(optionType) if optionType.accepts(value) => Right(())
      case Some(optionType)                              => Left(DotbotError.Decode(context.mismatch(key, optionType)))

  private def knownKeys: Vector[String] =
    fields.keys.toVector.sorted

object OptionSchema:
  /** Options accepted by a `link` entry, and by the `link` section of `defaults`. */
  val link: OptionSchema = OptionSchema(
    Map(
      "path"              -> OptionType.StrOrNull,
      "type"              -> OptionType.Str,
      "exclude"           -> OptionType.StrList,
      "prefix"            -> OptionType.Str,
      "if"                -> OptionType.Str,
      "relative"          -> OptionType.Bool,
      "canonicalize"      -> OptionType.Bool,
      "canonicalize-path" -> OptionType.Bool,
      "force"             -> OptionType.Bool,
      "relink"            -> OptionType.Bool,
      "create"            -> OptionType.Bool,
      "glob"              -> OptionType.Bool,
      "backup"            -> OptionType.Bool,
      "ignore-missing"    -> OptionType.Bool,
    ),
  )

  /** Options accepted by a `clean` entry, and by the `clean` section of `defaults`. */
  val clean: OptionSchema = OptionSchema(
    Map(
      "force"     -> OptionType.Bool,
      "recursive" -> OptionType.Bool,
    ),
  )

  /** Options accepted by a `create` entry, and by the `create` section of `defaults`. */
  val create: OptionSchema = OptionSchema(Map("mode" -> OptionType.Mode))

  /** Options accepted by a `shell` entry written as a map, and by the `shell` section of `defaults`. */
  val shell: OptionSchema = OptionSchema(
    Map(
      "command"     -> OptionType.Str,
      "description" -> OptionType.Str,
      "quiet"       -> OptionType.Bool,
      "stdin"       -> OptionType.Bool,
      "stdout"      -> OptionType.Bool,
      "stderr"      -> OptionType.Bool,
    ),
  )

  def forDirective(directive: Directive): Option[OptionSchema] =
    directive match
      case Directive.Link   => Some(link)
      case Directive.Clean  => Some(clean)
      case Directive.Create => Some(create)
      case Directive.Shell  => Some(shell)
      case _                => None
