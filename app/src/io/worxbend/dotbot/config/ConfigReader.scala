package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.EitherUtil
import io.worxbend.dotbot.core.PathUtil

import scala.util.control.NonFatal

final class ConfigReader:
  def read(paths: Seq[String]): Either[DotbotError, Vector[Task]] =
    EitherUtil.traverse(paths)(readOne).map(_.flatten)

  private def readOne(path: String): Either[DotbotError, Vector[Task]] =
    val fullPath = os.Path(PathUtil.path(path), os.pwd)
    readFile(path, fullPath).flatMap(ConfigParsers.parseTasks(path, fullPath.ext.toLowerCase, _))

  private def readFile(path: String, fullPath: os.Path): Either[DotbotError, String] =
    try Right(os.read(fullPath))
    catch case NonFatal(e) => Left(DotbotError.ConfigReadFailed(path, e.getMessage))
