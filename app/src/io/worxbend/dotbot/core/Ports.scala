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
 * Why a filesystem operation could not be carried out.
 *
 * Deliberately just the message. Every caller in this codebase reduces a failed filesystem call
 * to text for the log and moves on -- nothing inspects an exception type, rethrows, or reads a
 * stack trace. Naming a `java.lang.Throwable` in the port would let an outer-layer type cross
 * inward for no gain, and would force every fake filesystem to invent exception instances just
 * to satisfy the signature.
 */
final case class FsFailure(message: String)

/** The result of a filesystem call: the value asked for, or why it could not be produced. */
type FsResult[A] = Either[FsFailure, A]

/**
 * Filesystem abstraction for platform-safe command execution.
 */
trait Filesystem:
  def exists(path: String): Boolean
  def lexists(path: String): Boolean
  def isDir(path: String): Boolean
  def isSymlink(path: String): Boolean
  def listDir(path: String): FsResult[Vector[String]]

  /**
   * Every path in the tree under `root`, `root` itself included, no deeper than `maxDepth` levels
   * below it.
   *
   * Glob expansion needs to see a whole subtree at once, which `listDir` can only do by recursing
   * one directory at a time. Handing the walk to the port keeps the traversal — the part that
   * touches a real disk — on the outside, so that pattern matching stays testable against a fake.
   */
  def walk(root: String, maxDepth: Int): FsResult[Vector[String]]

  /**
   * Create a directory and any missing parents.
   *
   * Deliberately takes no mode: the underlying call applies the process umask, and a caller that
   * needs a specific mode follows up with `chmod`. The parameter used to be here and was silently
   * ignored by the implementation, which promised an atomicity the code never delivered.
   */
  def mkdirAll(path: String): FsResult[Unit]
  def chmod(path: String, mode: FileMode): FsResult[Unit]
  def readlink(path: String): FsResult[String]
  def realpath(path: String): FsResult[String]
  def remove(path: String): FsResult[Unit]
  def removeAll(path: String): FsResult[Unit]
  def rename(from: String, to: String): FsResult[Unit]
  def sameFile(a: String, b: String): FsResult[Boolean]
  def stat(path: String): FsResult[Unit]
  def symlink(target: String, link: String): FsResult[Unit]
  def hardlink(target: String, link: String): FsResult[Unit]

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

  def successful: Boolean =
    this match
      case ShellExit.Completed(0) => true
      case _                      => false

trait ShellRunner:
  def run(command: String, options: ShellOptions): ShellExit
