package io.worxbend.dotbot.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigValue as TypesafeConfigValue
import com.typesafe.config.ConfigValueType
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.EitherUtil
import org.tomlj.Toml
import org.virtuslab.yaml.Node
import org.virtuslab.yaml.Tag
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object ConfigParsers:
  def parseTasks(path: String, extension: String, data: String): Either[DotbotError, Vector[Task]] =
    parseValue(path, extension, data).flatMap(tasksFromValue(path, _))

  def parseValue(path: String, extension: String, data: String): Either[DotbotError, ConfigValue] =
    ConfigFormat.fromExtension(extension).flatMap(_.parse(path, data))

  private enum ConfigFormat:
    case Yaml
    case Hocon
    case Json
    case Toml

    def parse(path: String, data: String): Either[DotbotError, ConfigValue] =
      val parsed =
        this match
          case Yaml  => parseYaml(data)
          case Hocon => parseHocon(data, path)
          case Json  => parseJson(data)
          case Toml  => parseToml(data)
      // Each parser reports only what is wrong with the text. Naming the file it was reading is
      // the same job for all four, so it is done once here rather than at every failure site.
      parsed.left.map {
        case DotbotError.Message(detail) => DotbotError.ConfigParseFailed(path, detail)
        case other                       => other
      }

  private object ConfigFormat:
    def fromExtension(extension: String): Either[DotbotError, ConfigFormat] =
      extension match
        case "yaml" | "yml"   => Right(ConfigFormat.Yaml)
        case "conf" | "hocon" => Right(ConfigFormat.Hocon)
        case "json"           => Right(ConfigFormat.Json)
        case "toml"           => Right(ConfigFormat.Toml)
        case other            => Left(DotbotError.UnsupportedConfigFormat(other))

  private def parseYaml(data: String): Either[DotbotError, ConfigValue] =
    if data.trim.isEmpty then Right(ConfigValue.NullValue)
    else
      try
        org.virtuslab.yaml
          .parseYaml(data)
          .map(fromYaml)
          .left
          .map(error => DotbotError.Message(error.getMessage))
      catch case NonFatal(e) => Left(DotbotError.Message(e.getMessage))

  /**
   * Parse HOCON.
   *
   * Lightbend Config backs an object with a `HashMap`, so the fields of a task come back in hash
   * order rather than the order they were written. Order is significant in a Dotbot config — a
   * `defaults` block applies only to what follows it — so it is recovered from the line each field
   * was parsed from, and a task whose ordering cannot be recovered is rejected outright by
   * `checkTaskOrder` rather than guessed at.
   */
  private def parseHocon(data: String, path: String): Either[DotbotError, ConfigValue] =
    try
      val (configText, rootPath) = hoconInput(data)
      val options = ConfigParseOptions
        .defaults()
        .setSyntax(ConfigSyntax.CONF)
        .setOriginDescription(path)
      val parsed = ConfigFactory.parseString(configText, options)
      // `Config.getValue` reports a null value as a missing key, so the wrapper is read off the
      // root object instead, where an explicit null survives as a null value.
      val root: TypesafeConfigValue =
        rootPath.fold(parsed.root())(valuePath => parsed.root().get(valuePath))

      checkTaskOrder(path, root).map(_ => fromTypesafe(root))
    catch case NonFatal(e) => Left(DotbotError.Message(e.getMessage))

  /**
   * Parse JSON.
   *
   * jsoniter-scala reads the document as a stream of tokens, so an object's fields arrive in the
   * order they were written and the codec below simply appends them. No ordering has to be
   * recovered afterwards, unlike HOCON. The codec is written by hand rather than generated, which
   * keeps the parser free of both macros and reflection — the latter matters because this project
   * ships GraalVM native binaries.
   */
  private def parseJson(data: String): Either[DotbotError, ConfigValue] =
    if data.trim.isEmpty then Right(ConfigValue.NullValue)
    else
      try Right(readFromString(data)(using JsonConfigValueCodec))
      catch case NonFatal(e) => Left(DotbotError.Message(e.getMessage))

  private object JsonConfigValueCodec extends JsonValueCodec[ConfigValue]:
    def nullValue: ConfigValue = ConfigValue.NullValue

    def encodeValue(value: ConfigValue, out: JsonWriter): Unit =
      // Configuration is only ever read, never written back out.
      throw UnsupportedOperationException("dotbot does not write JSON configuration")

    def decodeValue(in: JsonReader, default: ConfigValue): ConfigValue =
      val token = in.nextToken()
      in.rollbackToken()
      token match
        case '"'       => ConfigValue.StringValue(in.readString(null))
        case 't' | 'f' => ConfigValue.BoolValue(in.readBoolean())
        case 'n'       => readNull(in)
        case '['       => readArray(in, default)
        case '{'       => readObject(in, default)
        case digit if digit == '-' || (digit >= '0' && digit <= '9') =>
          ConfigValue.NumberValue(in.readBigDecimal(null))
        case _ => in.decodeError("expected a JSON value")

    private def readNull(in: JsonReader): ConfigValue =
      // `readNullOrError` reads the rest of the literal and so needs the token it follows to be
      // the current one; the dispatch above rolled its lookahead back.
      in.nextToken(): Unit
      in.readNullOrError(ConfigValue.NullValue, "expected null")

    private def readArray(in: JsonReader, default: ConfigValue): ConfigValue =
      if !in.isNextToken('[') then in.decodeError("expected '['")
      else if in.isNextToken(']') then ConfigValue.ArrayValue(Vector.empty)
      else
        in.rollbackToken()
        val items = Vector.newBuilder[ConfigValue]
        while
          items += decodeValue(in, default)
          in.isNextToken(',')
        do ()
        if in.isCurrentToken(']') then ConfigValue.ArrayValue(items.result())
        else in.arrayEndOrCommaError()

    private def readObject(in: JsonReader, default: ConfigValue): ConfigValue =
      if !in.isNextToken('{') then in.decodeError("expected '{'")
      else if in.isNextToken('}') then ConfigValue.ObjectValue(Vector.empty)
      else
        in.rollbackToken()
        val fields = Vector.newBuilder[(String, ConfigValue)]
        while
          // Appending as the tokens arrive is what preserves the order the user wrote.
          fields += in.readKeyAsString() -> decodeValue(in, default)
          in.isNextToken(',')
        do ()
        if in.isCurrentToken('}') then ConfigValue.ObjectValue(fields.result())
        else in.objectEndOrCommaError()

  /**
   * Reject a task whose directives cannot be put back into the order they were written.
   *
   * Only the line a field came from is recorded, so two directives written on the same line inside
   * one task are indistinguishable. Rather than fall back to hash order — which is the silent
   * misbehavior this whole mechanism exists to prevent — such a task is reported, with the fix
   * spelled out. Directives on separate lines, and the usual one-directive-per-element style, are
   * unaffected.
   */
  private def checkTaskOrder(path: String, root: TypesafeConfigValue): Either[DotbotError, Unit] =
    EitherUtil.traverse(taskObjects(root))(checkOneTaskOrder(path, _)).map(_ => ())

  private def taskObjects(root: TypesafeConfigValue): Vector[ConfigObject] =
    val list =
      root match
        case items: ConfigList => Some(items)
        case obj: ConfigObject =>
          Vector("tasks", "task").iterator.map(obj.get).collectFirst { case items: ConfigList => items }
        case _ => None

    list.toVector.flatMap(_.asScala.toVector).collect { case obj: ConfigObject => obj }

  private def checkOneTaskOrder(path: String, task: ConfigObject): Either[DotbotError, Unit] =
    val sharingALine = task
      .entrySet()
      .asScala
      .toVector
      .groupBy(entry => entry.getValue.origin().lineNumber())
      .collectFirst { case (line, entries) if entries.size > 1 => line -> entries.map(_.getKey).sorted }

    sharingALine match
      case None => Right(())
      case Some((line, keys)) =>
        Left(DotbotError.AmbiguousTaskOrder(path, line, keys))

  private def parseToml(data: String): Either[DotbotError, ConfigValue] =
    try
      val result = Toml.parse(data)
      if result.hasErrors() then Left(DotbotError.Message(result.errors().asScala.map(_.toString).mkString("; ")))
      else Right(fromToml(result))
    catch case NonFatal(e) => Left(DotbotError.Message(e.getMessage))

  /**
   * Wrap a root-level list so that Lightbend Config, which requires an object at the root, can
   * parse it. The wrapper key is then unwrapped by the caller.
   */
  private def hoconInput(data: String): (String, Option[String]) =
    val trimmed = data.dropWhile(_.isWhitespace)
    if trimmed.startsWith("[") || isRootNull(trimmed) then s"tasks = $data" -> Some("tasks")
    else data -> None

  private def isRootNull(trimmed: String): Boolean =
    val nullToken = "null"
    trimmed == nullToken || (trimmed.startsWith(nullToken) && trimmed.drop(nullToken.length).headOption.exists(_.isWhitespace))

  private def tasksFromValue(path: String, value: ConfigValue): Either[DotbotError, Vector[Task]] =
    val list =
      value match
        case ConfigValue.NullValue            => Some(Vector.empty)
        case ConfigValue.ArrayValue(items)    => Some(items)
        case obj @ ConfigValue.ObjectValue(_) =>
          obj.field("tasks").orElse(obj.field("task")).flatMap(_.asArray)
        case _                                => None

    list match
      case None        => Left(DotbotError.ConfigRootNotTaskList(path))
      case Some(items) =>
        EitherUtil.traverse(items.zipWithIndex) { case (item, index) =>
          item match
            case ConfigValue.ObjectValue(fields) =>
              Right(Task(fields.map { case (key, data) => Action(Directive.parse(key), data) }))
            case _ => Left(DotbotError.ConfigTaskNotMapping(path, index + 1))
          }

  private[config] def fromYaml(node: Node): ConfigValue =
    node match
      case scalar: Node.ScalarNode =>
        scalar.tag match
          case Tag.nullTag => ConfigValue.NullValue
          case Tag.boolean => ConfigValue.BoolValue(scalar.value.equalsIgnoreCase("true"))
          case Tag.int     => ConfigValue.NumberValue(yamlInt(scalar.value))
          case Tag.float   => ConfigValue.NumberValue(BigDecimal(scalar.value.replace("_", "")))
          case _           => ConfigValue.StringValue(scalar.value)
      case sequence: Node.SequenceNode =>
        ConfigValue.ArrayValue(sequence.nodes.toVector.map(fromYaml))
      case mapping: Node.MappingNode =>
        ConfigValue.ObjectValue(
          mapping.mappings.toVector.map { case (key, value) => yamlKey(key) -> fromYaml(value) },
        )

  private def yamlKey(node: Node): String =
    node match
      case scalar: Node.ScalarNode => scalar.value
      case other                   => fromYaml(other).toString

  private def yamlInt(value: String): BigDecimal =
    val normalized = value.replace("_", "")
    val (sign, unsigned) =
      if normalized.startsWith("-") then -1 -> normalized.drop(1)
      else if normalized.startsWith("+") then 1 -> normalized.drop(1)
      else 1 -> normalized

    val parsed =
      if unsigned.startsWith("0x") then BigInt(unsigned.drop(2), 16)
      else if unsigned.startsWith("0o") then BigInt(unsigned.drop(2), 8)
      else BigInt(unsigned)

    BigDecimal(parsed * sign)

  private def fromTypesafe(value: TypesafeConfigValue): ConfigValue =
    value match
      case list: ConfigList     =>
        ConfigValue.ArrayValue(list.asScala.toVector.map(fromTypesafe))
      case obj: ConfigObject    =>
        ConfigValue.ObjectValue(typesafeFields(obj))
      case scalar               =>
        fromTypesafeScalar(scalar)

  /**
   * Read an object's fields in the order they were written in the file.
   *
   * `ConfigObject` extends `java.util.Map` and its `entrySet` iterates in hash order, which
   * scrambles the directives inside a task. That matters because a Dotbot config is an ordered
   * program, not a set of settings: a `defaults` block only applies to the directives that follow
   * it, so a reordered task silently applies the wrong options. YAML and TOML already preserve
   * order, and without this HOCON and JSON configs would behave differently from the identical
   * config written in YAML.
   *
   * Every value carries a `ConfigOrigin` recording the line it was parsed from, so sorting by line
   * restores the written order.
   */
  private def typesafeFields(obj: ConfigObject): Vector[(String, ConfigValue)] =
    obj
      .entrySet()
      .asScala
      .toVector
      // `sortBy` is stable, so fields that share a line keep their relative order. Lightbend Config
      // records only the line, not the column, so a task written inline as `{a = 1, b = 2}` cannot
      // be ordered from source; every task in the shipped examples is written one field per line.
      .sortBy(_.getValue.origin().lineNumber())
      .map(entry => entry.getKey -> fromTypesafe(entry.getValue))

  private def fromTypesafeScalar(value: TypesafeConfigValue): ConfigValue =
    value.valueType() match
      case ConfigValueType.NULL    => ConfigValue.NullValue
      case ConfigValueType.BOOLEAN => ConfigValue.BoolValue(value.unwrapped() == java.lang.Boolean.TRUE)
      case ConfigValueType.NUMBER  => ConfigValue.NumberValue(typesafeNumberValue(value.unwrapped()))
      case ConfigValueType.STRING  => ConfigValue.StringValue(value.unwrapped().toString)
      case _                       => ConfigValue.StringValue(value.unwrapped().toString)

  private def typesafeNumberValue(value: Matchable): BigDecimal =
    value match
      case item: java.lang.Number => numberValue(item)
      case other                  => BigDecimal(other.toString)

  private def numberValue(value: java.lang.Number): BigDecimal =
    value match
      case item: java.lang.Byte       => BigDecimal(item.toLong)
      case item: java.lang.Short      => BigDecimal(item.toLong)
      case item: java.lang.Integer    => BigDecimal(item.toLong)
      case item: java.lang.Long       => BigDecimal(item.longValue())
      case item: java.lang.Float      => BigDecimal.decimal(item.toDouble)
      case item: java.lang.Double     => BigDecimal.decimal(item.doubleValue())
      case item: java.math.BigInteger => BigDecimal(item)
      case item: java.math.BigDecimal => BigDecimal(item)
      case other                      => BigDecimal(other.toString)

  private def fromToml(value: Matchable | Null): ConfigValue =
    Option(value).fold(ConfigValue.NullValue)(fromTomlValue)

  private def fromTomlValue(value: Matchable): ConfigValue =
    value match
      case item: java.lang.Boolean     => ConfigValue.BoolValue(item.booleanValue())
      case item: java.lang.Number      => ConfigValue.NumberValue(numberValue(item))
      case item: String                => ConfigValue.StringValue(item)
      case item: org.tomlj.TomlTable   =>
        ConfigValue.ObjectValue(
          item.entrySet().asScala.toVector.map(entry => entry.getKey -> fromToml(entry.getValue)),
        )
      case item: org.tomlj.TomlArray   =>
        ConfigValue.ArrayValue(item.toList.asScala.toVector.map(fromToml))
      case other                       => ConfigValue.StringValue(other.toString)
