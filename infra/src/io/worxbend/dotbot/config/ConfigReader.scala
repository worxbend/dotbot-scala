package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.ConfigLoader
import io.worxbend.dotbot.core.EitherUtil
import io.worxbend.dotbot.core.PathResolver
import io.worxbend.dotbot.core.Task

import scala.util.control.NonFatal

/**
 * Reads configuration files from disk and hands their text to the format parsers.
 *
 * @param paths resolver used to expand `~` and environment variables in the given file paths.
 */
final class ConfigReader(paths: PathResolver = PathResolver.system) extends ConfigLoader:
  def read(configFilePaths: Seq[String]): Either[DotbotError, Vector[Task]] =
    EitherUtil.traverse(configFilePaths)(readOne).map(_.flatten)

  private def readOne(path: String): Either[DotbotError, Vector[Task]] =
    val read =
      try
        val fullPath = os.Path(paths.abs(path))
        Right(fullPath.ext.toLowerCase -> os.read(fullPath))
      catch case NonFatal(error) => Left(DotbotError.ConfigReadFailed(path, error.getMessage))
    read.flatMap { case (extension, data) => ConfigParsers.parseTasks(path, extension, data) }
