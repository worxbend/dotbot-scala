package io.worxbend.dotbot.core

extension (values: Map[String, ConfigValue])
  def sortedKeys: Vector[String] =
    values.keys.toVector.sorted

  def boolValue(key: String, fallback: Boolean): Boolean =
    values.get(key).flatMap(_.asBoolean).getOrElse(fallback)

extension (value: ConfigValue)
  def isStringList: Boolean =
    value.asArray.exists(_.forall(_.asString.nonEmpty))
