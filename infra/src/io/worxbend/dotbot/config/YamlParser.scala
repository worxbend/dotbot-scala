package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.DotbotError

import org.virtuslab.yaml.Node
import org.virtuslab.yaml.Tag

/** Reads YAML, which already gives back an object's fields in the order they were written. */
private[config] object YamlParser:
  def parse(data: String): Either[DotbotError, ConfigValue] =
    if data.trim.isEmpty then Right(ConfigValue.NullValue)
    else
      org.virtuslab.yaml
        .parseYaml(data)
        .map(fromYaml)
        .left
        .map(error => DotbotError.Message(error.getMessage))

  private def fromYaml(node: Node): ConfigValue =
    node match
      case scalar: Node.ScalarNode     =>
        scalar.tag match
          case Tag.nullTag => ConfigValue.NullValue
          case Tag.boolean => ConfigValue.BoolValue(scalar.value.equalsIgnoreCase("true"))
          case Tag.int     => ConfigValue.NumberValue(yamlInt(scalar.value))
          case Tag.float   => ConfigValue.NumberValue(BigDecimal(scalar.value.replace("_", "")))
          case _           => ConfigValue.StringValue(scalar.value)
      case sequence: Node.SequenceNode =>
        ConfigValue.ArrayValue(sequence.nodes.toVector.map(fromYaml))
      case mapping: Node.MappingNode   =>
        ConfigValue.ObjectValue(
          mapping.mappings.toVector.map { case (key, value) => yamlKey(key) -> fromYaml(value) },
        )

  private def yamlKey(node: Node): String =
    node match
      case scalar: Node.ScalarNode => scalar.value
      case other                   => fromYaml(other).toString

  private def yamlInt(value: String): BigDecimal =
    val normalized       = value.replace("_", "")
    val (sign, unsigned) =
      if normalized.startsWith("-") then -1 -> normalized.drop(1)
      else if normalized.startsWith("+") then 1 -> normalized.drop(1)
      else 1                                    -> normalized

    val parsed =
      if unsigned.startsWith("0x") then BigInt(unsigned.drop(2), 16)
      else if unsigned.startsWith("0o") then BigInt(unsigned.drop(2), 8)
      else BigInt(unsigned)

    BigDecimal(parsed * sign)
