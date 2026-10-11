package io.worxbend.dotbot.core

extension (values: Map[String, ConfigValue])
  def sortedKeys: Vector[String] =
    values.keys.toVector.sorted

  def boolValue(key: String, fallback: Boolean): Boolean =
    values.get(key).flatMap(_.asBoolean).getOrElse(fallback)

extension (value: ConfigValue)
  def isStringList: Boolean =
    value.asArray.exists(_.forall(_.asString.nonEmpty))

  /**
   * A short, one-line rendering of a value, for error messages that need to quote what the user
   * actually wrote.
   */
  def describe: String =
    value match
      case ConfigValue.NullValue           => "null"
      case ConfigValue.BoolValue(item)     => item.toString
      case ConfigValue.NumberValue(item)   => item.toString
      case ConfigValue.StringValue(item)   => s"\"$item\""
      case ConfigValue.ArrayValue(items)   => items.map(_.describe).mkString("[", ", ", "]")
      case ConfigValue.ObjectValue(fields) =>
        fields.map { case (key, item) => s"$key: ${item.describe}" }.mkString("{", ", ", "}")
