package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.EitherUtil
import io.worxbend.dotbot.core.PathResolver
import io.worxbend.dotbot.core.Task

import scala.util.control.NonFatal

/**
 * Reads configuration files from disk and hands their text to the format parsers.
 *
 * @param paths resolver used to expand `~` and environment variables in the given file paths.
 */
final class ConfigReader(paths: PathResolver = PathResolver.system):
  def read(configFilePaths: Seq[String]): Either[DotbotError, Vector[Task]] =
    EitherUtil.traverse(configFilePaths)(readOne).map(_.flatten)

  private def readOne(path: String): Either[DotbotError, Vector[Task]] =
    val fullPath = os.Path(paths.path(path), os.pwd)
    readFile(path, fullPath).flatMap(ConfigParsers.parseTasks(path, fullPath.ext.toLowerCase, _))

  private def readFile(path: String, fullPath: os.Path): Either[DotbotError, String] =
    try Right(os.read(fullPath))
    catch case NonFatal(e) => Left(DotbotError.ConfigReadFailed(path, e.getMessage))
