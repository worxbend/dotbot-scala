package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.PathUtil

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.tomlj.Toml

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

final class ConfigReader:
  def read(paths: Seq[String]): Either[String, Vector[Task]] =
    paths.foldLeft(Right(Vector.empty): Either[String, Vector[Task]]) { (acc, path) =>
      for
        tasks <- acc
        next  <- readOne(path)
      yield tasks ++ next
    }

  private def readOne(path: String): Either[String, Vector[Task]] =
    val fullPath = PathUtil.path(path)
    val extension = os.Path(fullPath, os.pwd).ext.toLowerCase

    val raw =
      try Right(os.read(os.Path(fullPath, os.pwd)))
      catch case NonFatal(e) => Left(s"could not read config file \"$path\": ${e.getMessage}")

    raw.flatMap { data =>
      val parsed = extension match
        case "yaml" | "yml" => parseYaml(data)
        case "json"         => parseJson(data)
        case "json5"        => parseJson5(data)
        case "toml"         => parseToml(data)
        case other          => Left(s"unsupported config file format .$other")

      parsed.flatMap(tasksFromValue(path, _))
    }

  private def parseYaml(data: String): Either[String, ConfigValue] =
    try
      val settings = LoadSettings.builder().build()
      Right(ConfigValue.fromJava(Load(settings).loadFromString(data)))
    catch case NonFatal(e) => Left(e.getMessage)

  private def parseJson(data: String): Either[String, ConfigValue] =
    try Right(ConfigValue.fromUJson(ujson.read(data)))
    catch case NonFatal(e) => Left(e.getMessage)

  private def parseJson5(data: String): Either[String, ConfigValue] =
    try Right(ConfigValue.fromJson5(de.marhali.json5.Json5().parse(data)))
    catch case NonFatal(e) => Left(e.getMessage)

  private def parseToml(data: String): Either[String, ConfigValue] =
    try
      val result = Toml.parse(data)
      if result.hasErrors() then Left(result.errors().asScala.map(_.toString).mkString("; "))
      else Right(ConfigValue.fromJava(result))
    catch case NonFatal(e) => Left(e.getMessage)

  private def tasksFromValue(path: String, value: ConfigValue): Either[String, Vector[Task]] =
    val list =
      value match
        case ConfigValue.NullValue => Some(Vector.empty)
        case ConfigValue.ArrayValue(items) => Some(items)
        case obj @ ConfigValue.ObjectValue(_) =>
          obj.field("tasks").orElse(obj.field("task")).flatMap(_.asArray)
        case _ => None

    list match
      case None => Left(s"configuration file \"$path\" must be a list of tasks")
      case Some(items) =>
        items.zipWithIndex
          .foldLeft(Right(Vector.empty): Either[String, Vector[Task]]) { case (acc, (item, index)) =>
            acc.flatMap { tasks =>
              item match
                case ConfigValue.ObjectValue(fields) => Right(tasks :+ Task(fields.map { case (key, data) => Action(key, data) }))
                case _                               => Left(s"configuration task ${index + 1} in \"$path\" must be a mapping")
            }
          }
