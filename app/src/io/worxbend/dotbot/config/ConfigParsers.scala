package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.Action
import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.EitherUtil
import io.worxbend.dotbot.core.Task

/**
 * Picks a parser by file extension and turns what it produces into the ordered task list the rest
 * of the pipeline works on. The formats themselves live in one object each.
 */
object ConfigParsers:
  def parseTasks(path: String, extension: String, data: String): Either[DotbotError, Vector[Task]] =
    parseValue(path, extension, data).flatMap(tasksFromValue(path, _))

  def parseValue(path: String, extension: String, data: String): Either[DotbotError, ConfigValue] =
    ConfigFormat.fromExtension(extension).flatMap(_.parse(path, data))

  private enum ConfigFormat:
    case Yaml
    case Hocon
    case Json
    case Toml

    def parse(path: String, data: String): Either[DotbotError, ConfigValue] =
      val parsed =
        this match
          case Yaml  => YamlParser.parse(data)
          case Hocon => HoconParser.parse(data, path)
          case Json  => JsonParser.parse(data)
          case Toml  => TomlParser.parse(data)
      // Each parser reports only what is wrong with the text. Naming the file it was reading is
      // the same job for all four, so it is done once here rather than at every failure site.
      parsed.left.map {
        case DotbotError.Message(detail) => DotbotError.ConfigParseFailed(path, detail)
        case other                       => other
      }

  private object ConfigFormat:
    def fromExtension(extension: String): Either[DotbotError, ConfigFormat] =
      extension match
        case "yaml" | "yml"   => Right(ConfigFormat.Yaml)
        case "conf" | "hocon" => Right(ConfigFormat.Hocon)
        case "json"           => Right(ConfigFormat.Json)
        case "toml"           => Right(ConfigFormat.Toml)
        case other            => Left(DotbotError.UnsupportedConfigFormat(other))

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
