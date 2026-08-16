package io.worxbend.dotbot.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigValue as TypesafeConfigValue
import com.typesafe.config.ConfigValueType
import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.EitherUtil

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * Reads HOCON, the one format whose parser does not preserve field order on its own.
 *
 * This is the largest of the four parsers because of that: order has to be recovered from the
 * line each field was parsed from, and a task whose order cannot be recovered has to be rejected.
 */
private[config] object HoconParser:
  /**
   * Parse HOCON.
   *
   * Lightbend Config backs an object with a `HashMap`, so the fields of a task come back in hash
   * order rather than the order they were written. Order is significant in a Dotbot config — a
   * `defaults` block applies only to what follows it — so it is recovered from the line each field
   * was parsed from, and a task whose ordering cannot be recovered is rejected outright by
   * `checkTaskOrder` rather than guessed at.
   */
  def parse(data: String, path: String): Either[DotbotError, ConfigValue] =
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
      case item: java.lang.Number => ConfigNumbers.numberValue(item)
      case other                  => BigDecimal(other.toString)
