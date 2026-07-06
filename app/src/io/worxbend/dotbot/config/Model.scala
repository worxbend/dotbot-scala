package io.worxbend.dotbot.config

import scala.jdk.CollectionConverters.*

enum ConfigValue:
  case NullValue
  case BoolValue(value: Boolean)
  case NumberValue(value: BigDecimal)
  case StringValue(value: String)
  case ArrayValue(items: Vector[ConfigValue])
  case ObjectValue(fields: Vector[(String, ConfigValue)])

  def asString: Option[String] =
    this match
      case ConfigValue.StringValue(value) => Some(value)
      case _                              => None

  def asBoolean: Option[Boolean] =
    this match
      case ConfigValue.BoolValue(value) => Some(value)
      case _                           => None

  def asArray: Option[Vector[ConfigValue]] =
    this match
      case ConfigValue.ArrayValue(items) => Some(items)
      case _                             => None

  def asObject: Option[Vector[(String, ConfigValue)]] =
    this match
      case ConfigValue.ObjectValue(fields) => Some(fields)
      case _                               => None

  def asMap: Option[Map[String, ConfigValue]] =
    asObject.map(_.toMap)

  def field(name: String): Option[ConfigValue] =
    asObject.flatMap(_.find(_._1 == name).map(_._2))

object ConfigValue:
  def fromJava(value: Any): ConfigValue =
    value match
      case null                         => NullValue
      case value: java.lang.Boolean     => BoolValue(value.booleanValue())
      case value: java.lang.Byte        => NumberValue(BigDecimal(value.toLong))
      case value: java.lang.Short       => NumberValue(BigDecimal(value.toLong))
      case value: java.lang.Integer     => NumberValue(BigDecimal(value.toLong))
      case value: java.lang.Long        => NumberValue(BigDecimal(value.longValue()))
      case value: java.lang.Float       => NumberValue(BigDecimal.decimal(value.toDouble))
      case value: java.lang.Double      => NumberValue(BigDecimal.decimal(value.doubleValue()))
      case value: java.math.BigInteger  => NumberValue(BigDecimal(value))
      case value: java.math.BigDecimal  => NumberValue(BigDecimal(value))
      case value: String                => StringValue(value)
      case value: org.tomlj.TomlTable   => ObjectValue(value.entrySet().asScala.toVector.map(entry => entry.getKey -> fromJava(entry.getValue)))
      case value: org.tomlj.TomlArray   => ArrayValue(value.toList.asScala.toVector.map(fromJava))
      case value: java.util.Map[?, ?]   => ObjectValue(value.asScala.toVector.map { case (key, item) => key.toString -> fromJava(item) })
      case value: java.lang.Iterable[?] => ArrayValue(value.asScala.toVector.map(fromJava))
      case value: Array[?]              => ArrayValue(value.toVector.map(fromJava))
      case other                        => StringValue(other.toString)

  def fromUJson(value: ujson.Value): ConfigValue =
    value match
      case ujson.Null       => NullValue
      case ujson.Bool(item) => BoolValue(item)
      case ujson.Num(item)  => NumberValue(BigDecimal.decimal(item))
      case ujson.Str(item)  => StringValue(item)
      case array: ujson.Arr => ArrayValue(array.value.toVector.map(fromUJson))
      case obj: ujson.Obj   => ObjectValue(obj.value.toVector.map { case (key, item) => key -> fromUJson(item) })

  def fromJson5(value: de.marhali.json5.Json5Element): ConfigValue =
    if value == null || value.isJson5Null then NullValue
    else if value.isJson5Array then
      ArrayValue(value.getAsJson5Array.iterator().asScala.toVector.map(fromJson5))
    else if value.isJson5Object then
      ObjectValue(value.getAsJson5Object.entrySet().asScala.toVector.map(entry => entry.getKey -> fromJson5(entry.getValue)))
    else
      val primitive = value.getAsJson5Primitive
      if primitive.isBoolean then BoolValue(primitive.getAsBoolean)
      else if primitive.isNumber then NumberValue(BigDecimal(primitive.getAsBigDecimal))
      else StringValue(primitive.getAsString)

final case class Action(directive: String, data: ConfigValue)

final case class Task(actions: Vector[Action])
