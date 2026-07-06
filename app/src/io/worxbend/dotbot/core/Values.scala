package io.worxbend.dotbot.core

import io.worxbend.dotbot.config.ConfigValue

object Values:
  def asMap(value: ConfigValue): Option[Map[String, ConfigValue]] =
    value.asMap

  def asList(value: ConfigValue): Option[Vector[ConfigValue]] =
    value.asArray

  def asString(value: ConfigValue): Option[String] =
    value.asString

  def sortedKeys(map: Map[String, ConfigValue]): Vector[String] =
    map.keys.toVector.sorted

  def boolValue(map: Map[String, ConfigValue], key: String, fallback: Boolean): Boolean =
    map.get(key).flatMap(_.asBoolean).getOrElse(fallback)

  def stringValue(map: Map[String, ConfigValue], key: String, fallback: String): String =
    map.get(key).flatMap(_.asString).getOrElse(fallback)

  def stringSlice(value: ConfigValue): Vector[String] =
    value.asArray.toVector.flatten.flatMap(_.asString)

  def isStringList(value: ConfigValue): Boolean =
    value.asArray.exists(_.forall(_.asString.nonEmpty))

  def defaultTarget(linkName: String, target: ConfigValue): String =
    target match
      case ConfigValue.NullValue =>
        val base = PathUtil.basename(linkName)
        if base.startsWith(".") then base.drop(1) else base
      case _ => target.asString.getOrElse("")

  def modeValue(value: Option[ConfigValue], fallback: Int): Int =
    value match
      case Some(ConfigValue.StringValue(item)) =>
        scala.util.Try(Integer.parseInt(item, 8)).getOrElse(fallback)
      case Some(ConfigValue.NumberValue(item)) => item.toInt
      case _                                  => fallback
