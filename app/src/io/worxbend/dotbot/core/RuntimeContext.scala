package io.worxbend.dotbot.core

import java.time.Clock
import java.time.Duration

final case class CoreOptions(
  only:          Set[Directive] = Set.empty,
  skip:          Set[Directive] = Set.empty,
  exitOnFailure: Boolean = false,
  dryRun:        Boolean = false,
  verbose:       Int = 0,
  shellTimeout:  Duration = Duration.ofMinutes(10),
)

final case class RuntimeContext(
  baseDirectory: String,
  options:       CoreOptions,
  log:           Log,
  fs:            Filesystem,
  shell:         ShellRunner,
  clock:         Clock,
  paths:         PathResolver = PathResolver.system,
):
  def withFilesystem[A](result: => Either[Throwable, A], onFailure: Throwable => String): Option[A] =
    result match
      case Left(error) =>
        log.warning(onFailure(error))
        log.debug(error.getMessage)
        None
      case Right(value) =>
        Some(value)

  def commandFailed(exit: ShellExit, message: String): Unit =
    if !exit.successful then log.warning(message)

  def runShell(command: String, options: ShellOptions, onFailure: ShellExit => String): Outcome =
    val exit = shell.run(command, options)
    if exit.successful then Outcome.Ok else
      log.warning(onFailure(exit))
      Outcome.Failed
