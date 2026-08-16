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
      case _                            => None

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

  def asFields: Option[ConfigFields] =
    asObject.map(ConfigFields.apply)

  def field(name: String): Option[ConfigValue] =
    asObject.flatMap(_.find(_._1 == name).map(_._2))

/**
 * An object's fields, keeping the order they were written in alongside a map view for lookup.
 *
 * A Dotbot config is an ordered program rather than a set of settings, and that applies inside a
 * directive as well as between them: one `link` entry's `if` condition can depend on an earlier
 * entry having run, and a `create` entry can establish the directory a later link needs.
 * [[ConfigValue.asMap]] answers lookups but loses that order, so anything that *iterates* a
 * directive's entries uses this instead.
 *
 * A repeated key keeps its first position and its last value, which is how a map would have read
 * it; only the ordering changes.
 */
final case class ConfigFields(entries: Vector[(String, ConfigValue)]):
  val values: Map[String, ConfigValue] = entries.toMap

  /** The field names in written order, without repeats. */
  def keys: Vector[String] = entries.map(_._1).distinct

  def apply(key: String): ConfigValue = values(key)

final case class Action(directive: Directive, data: ConfigValue)

final case class Task(actions: Vector[Action])
