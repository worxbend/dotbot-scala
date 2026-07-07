package io.worxbend.dotbot.core

import java.time.Duration

/**
 * Logging abstraction used by core runtime components.
 */
trait Log:
  def debug(message: String): Unit
  def info(message: String): Unit
  def action(message: String): Unit
  def warning(message: String): Unit
  def error(message: String): Unit

/**
 * Filesystem abstraction for platform-safe command execution.
 */
trait Filesystem:
  def exists(path: String): Boolean
  def lexists(path: String): Boolean
  def isDir(path: String): Boolean
  def isSymlink(path: String): Boolean
  def listDir(path: String): Either[Throwable, Vector[String]]
  def mkdirAll(path: String, mode: FileMode): Either[Throwable, Unit]
  def chmod(path: String, mode: FileMode): Either[Throwable, Unit]
  def readlink(path: String): Either[Throwable, String]
  def realpath(path: String): Either[Throwable, String]
  def remove(path: String): Either[Throwable, Unit]
  def removeAll(path: String): Either[Throwable, Unit]
  def rename(from: String, to: String): Either[Throwable, Unit]
  def sameFile(a: String, b: String): Either[Throwable, Boolean]
  def stat(path: String): Either[Throwable, Unit]
  def symlink(target: String, link: String): Either[Throwable, Unit]
  def hardlink(target: String, link: String): Either[Throwable, Unit]

final case class ShellOptions(
  cwd:          String,
  enableStdin:  Boolean = false,
  enableStdout: Boolean = false,
  enableStderr: Boolean = false,
  timeout:      Duration = Duration.ofMinutes(10),
)

enum ShellExit:
  case Completed(exitCode: Int)
  case TimedOut

  def code: Int =
    this match
      case ShellExit.Completed(value) => value
      case ShellExit.TimedOut         => 124

  def successful: Boolean =
    this match
      case ShellExit.Completed(0) => true
      case _                      => false

trait ShellRunner:
  def run(command: String, options: ShellOptions): ShellExit
