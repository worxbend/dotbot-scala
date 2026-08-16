package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.DotbotError

import org.tomlj.Toml
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Reads TOML, which like YAML preserves the order its tables were written in. */
private[config] object TomlParser:
  def parse(data: String): Either[DotbotError, ConfigValue] =
    try
      val result = Toml.parse(data)
      if result.hasErrors() then Left(DotbotError.Message(result.errors().asScala.map(_.toString).mkString("; ")))
      else Right(fromToml(result))
    catch case NonFatal(e) => Left(DotbotError.Message(e.getMessage))

  private def fromToml(value: Matchable | Null): ConfigValue =
    Option(value).fold(ConfigValue.NullValue)(fromTomlValue)
  
  private def fromTomlValue(value: Matchable): ConfigValue =
    value match
      case item: java.lang.Boolean     => ConfigValue.BoolValue(item.booleanValue())
      case item: java.lang.Number      => ConfigValue.NumberValue(ConfigNumbers.numberValue(item))
      case item: String                => ConfigValue.StringValue(item)
      case item: org.tomlj.TomlTable   =>
        ConfigValue.ObjectValue(
          item.entrySet().asScala.toVector.map(entry => entry.getKey -> fromToml(entry.getValue)),
        )
      case item: org.tomlj.TomlArray   =>
        ConfigValue.ArrayValue(item.toList.asScala.toVector.map(fromToml))
      case other                       => ConfigValue.StringValue(other.toString)
