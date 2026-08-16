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

  /**
   * Every path in the tree under `root`, `root` itself included, no deeper than `maxDepth` levels
   * below it.
   *
   * Glob expansion needs to see a whole subtree at once, which `listDir` can only do by recursing
   * one directory at a time. Handing the walk to the port keeps the traversal — the part that
   * touches a real disk — on the outside, so that pattern matching stays testable against a fake.
   */
  def walk(root: String, maxDepth: Int): Either[Throwable, Vector[String]]

  /**
   * Create a directory and any missing parents.
   *
   * Deliberately takes no mode: the underlying call applies the process umask, and a caller that
   * needs a specific mode follows up with `chmod`. The parameter used to be here and was silently
   * ignored by the implementation, which promised an atomicity the code never delivered.
   */
  def mkdirAll(path: String): Either[Throwable, Unit]
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
    cwd: String,
    enableStdin: Boolean = false,
    enableStdout: Boolean = false,
    enableStderr: Boolean = false,
    timeout: Duration = Duration.ofMinutes(10),
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
