package io.worxbend.dotbot.core

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

final case class Action(directive: Directive, data: ConfigValue)

final case class Task(actions: Vector[Action])
