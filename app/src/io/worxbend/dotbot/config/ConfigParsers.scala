package io.worxbend.dotbot.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigValue as TypesafeConfigValue
import com.typesafe.config.ConfigValueType
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.EitherUtil
import org.tomlj.Toml
import org.virtuslab.yaml.Node
import org.virtuslab.yaml.Tag
import pureconfig.ConfigReader as PureConfigReader
import pureconfig.ConfigSource

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object ConfigParsers:
  private given PureConfigReader[ConfigValue] =
    PureConfigReader.fromCursor(_.asConfigValue.map(fromTypesafe))

  def parseTasks(path: String, extension: String, data: String): Either[DotbotError, Vector[Task]] =
    parseValue(path, extension, data).flatMap(tasksFromValue(path, _))

  def parseValue(path: String, extension: String, data: String): Either[DotbotError, ConfigValue] =
    ConfigFormat.fromExtension(extension).flatMap(_.parse(path, data))

  private enum ConfigFormat:
    case Yaml
    case Lightbend(syntax: ConfigSyntax)
    case Toml

    def parse(path: String, data: String): Either[DotbotError, ConfigValue] =
      this match
        case Yaml              => parseYaml(data)
        case Lightbend(syntax) => parseLightbend(data, syntax, path)
        case Toml              => parseToml(data)

  private object ConfigFormat:
    def fromExtension(extension: String): Either[DotbotError, ConfigFormat] =
      extension match
        case "yaml" | "yml"   => Right(ConfigFormat.Yaml)
        case "conf" | "hocon" => Right(ConfigFormat.Lightbend(ConfigSyntax.CONF))
        case "json" => Right(ConfigFormat.Lightbend(ConfigSyntax.JSON))
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

  private def parseLightbend(
    data: String,
    syntax: ConfigSyntax,
    path: String,
  ): Either[DotbotError, ConfigValue] =
    try
      val (configText, rootPath) = lightbendInput(data, syntax)
      val options = ConfigParseOptions
        .defaults()
        .setSyntax(syntax)
        .setOriginDescription(path)
      val source = ConfigSource.fromConfig(ConfigFactory.parseString(configText, options))
      val result = rootPath match
        case Some(valuePath) => source.at(valuePath).load[ConfigValue]
        case None            => source.load[ConfigValue]

      result.left.map(error => DotbotError.Message(error.prettyPrint()))
    catch case NonFatal(e) => Left(DotbotError.Message(e.getMessage))

  private def parseToml(data: String): Either[DotbotError, ConfigValue] =
    try
      val result = Toml.parse(data)
      if result.hasErrors() then Left(DotbotError.Message(result.errors().asScala.map(_.toString).mkString("; ")))
      else Right(fromToml(result))
    catch case NonFatal(e) => Left(DotbotError.Message(e.getMessage))

  private def lightbendInput(data: String, syntax: ConfigSyntax): (String, Option[String]) =
    val trimmed = data.dropWhile(_.isWhitespace)
    if trimmed.startsWith("[") || isRootNull(trimmed) then
      val wrapped = syntax match
        case ConfigSyntax.JSON => s"""{"tasks": $data}"""
        case _                 => s"tasks = $data"
      wrapped -> Some("tasks")
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
        ConfigValue.ObjectValue(
          obj.entrySet().asScala.toVector.map(entry => entry.getKey -> fromTypesafe(entry.getValue)),
        )
      case scalar               =>
        fromTypesafeScalar(scalar)

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
