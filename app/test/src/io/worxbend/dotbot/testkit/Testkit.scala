package io.worxbend.dotbot.testkit

import io.worxbend.dotbot.core.CoreOptions
import io.worxbend.dotbot.core.Environment
import io.worxbend.dotbot.core.FileMode
import io.worxbend.dotbot.core.Filesystem
import io.worxbend.dotbot.core.PathResolver
import io.worxbend.dotbot.core.RuntimeContext
import io.worxbend.dotbot.core.ShellExit
import io.worxbend.dotbot.core.ShellOptions
import io.worxbend.dotbot.core.ShellRunner
import io.worxbend.dotbot.logging.Level
import io.worxbend.dotbot.logging.Logger

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.time.Clock
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map as MutableMap

enum FakeEntry:
  case Directory
  case File
  case Symlink(target: String)

final class FakeFilesystem(initial: Map[String, FakeEntry]) extends Filesystem:
  private val entries: MutableMap[String, FakeEntry] =
    MutableMap.from(initial.map { case (path, entry) => normalize(path) -> entry })

  private val mkdirCalls0 = ArrayBuffer.empty[String]
  private val chmodCalls0 = ArrayBuffer.empty[(String, FileMode)]
  private val removeCalls0 = ArrayBuffer.empty[String]

  def mkdirCalls: Vector[String] = mkdirCalls0.toVector
  def chmodCalls: Vector[(String, FileMode)] = chmodCalls0.toVector
  def removeCalls: Vector[String] = removeCalls0.toVector
  def paths: Set[String] = entries.keySet.toSet

  def hasDirectory(path: String): Boolean =
    entries.get(normalize(path)).contains(FakeEntry.Directory)

  def exists(path: String): Boolean =
    entries.get(normalize(path)) match
      case Some(FakeEntry.Symlink(target)) => resolveLink(path, target).exists(entries.contains)
      case Some(_)                         => true
      case None                            => false

  def lexists(path: String): Boolean =
    entries.contains(normalize(path))

  def isDir(path: String): Boolean =
    entries.get(normalize(path)) match
      case Some(FakeEntry.Directory)       => true
      case Some(FakeEntry.Symlink(target)) =>
        resolveLink(path, target).exists(resolved => entries.get(resolved).contains(FakeEntry.Directory))
      case _                               => false

  def isSymlink(path: String): Boolean =
    entries.get(normalize(path)).exists {
      case FakeEntry.Symlink(_) => true
      case _                    => false
    }

  def listDir(path: String): Either[Throwable, Vector[String]] =
    val dir = normalize(path)
    if !isDir(dir) then Left(NoSuchFileException(dir))
    else
      Right(
        entries.keysIterator
          .filter(item => parent(item).contains(dir))
          .map(item => Paths.get(item).getFileName.toString)
          .toVector
          .sorted,
      )

  def mkdirAll(path: String): Either[Throwable, Unit] =
    val normalized = normalize(path)
    entries.update(normalized, FakeEntry.Directory)
    mkdirCalls0 += normalized
    Right(())

  def chmod(path: String, mode: FileMode): Either[Throwable, Unit] =
    val normalized = normalize(path)
    if lexists(normalized) then
      chmodCalls0 += (normalized -> mode)
      Right(())
    else Left(NoSuchFileException(normalized))

  def readlink(path: String): Either[Throwable, String] =
    entries.get(normalize(path)) match
      case Some(FakeEntry.Symlink(target)) => Right(target)
      case _                               => Left(NoSuchFileException(path))

  def realpath(path: String): Either[Throwable, String] =
    if exists(path) then Right(normalize(path)) else Left(NoSuchFileException(path))

  def remove(path: String): Either[Throwable, Unit] =
    val normalized = normalize(path)
    if lexists(normalized) then
      removeEntry(normalized)
      removeCalls0 += normalized
      Right(())
    else Left(NoSuchFileException(normalized))

  def removeAll(path: String): Either[Throwable, Unit] =
    val normalized = normalize(path)
    val removed = entries.keysIterator
      .filter(item => item == normalized || item.startsWith(normalized + "/"))
      .toVector
    removed.foreach(removeEntry)
    removeCalls0 += normalized
    Right(())

  def rename(from: String, to: String): Either[Throwable, Unit] =
    val normalizedFrom = normalize(from)
    val normalizedTo = normalize(to)
    entries.get(normalizedFrom) match
      case Some(entry) =>
        removeEntry(normalizedFrom)
        entries.update(normalizedTo, entry)
        Right(())
      case None => Left(NoSuchFileException(normalizedFrom))

  def sameFile(a: String, b: String): Either[Throwable, Boolean] =
    Right(normalize(a) == normalize(b))

  def stat(path: String): Either[Throwable, Unit] =
    if exists(path) then Right(()) else Left(NoSuchFileException(path))

  def symlink(target: String, link: String): Either[Throwable, Unit] =
    entries.update(normalize(link), FakeEntry.Symlink(target))
    Right(())

  def hardlink(target: String, link: String): Either[Throwable, Unit] =
    if exists(target) then
      entries.update(normalize(link), FakeEntry.File)
      Right(())
    else Left(NoSuchFileException(target))

  private def resolveLink(path: String, target: String): Option[String] =
    val targetPath = Paths.get(target)
    val resolved =
      if targetPath.isAbsolute then targetPath
      else Paths.get(normalize(path)).getParent.resolve(targetPath)
    Some(normalize(resolved.toString))

  private def parent(path: String): Option[String] =
    Option(Paths.get(path).getParent).map(_.normalize().toString)

  private def normalize(path: String): String =
    Paths.get(path).normalize().toString

  private def removeEntry(path: String): Unit =
    entries.remove(path) match
      case _ => ()

object FakeFilesystem:
  def empty: FakeFilesystem =
    FakeFilesystem(Map.empty)

  def apply(entries: Map[String, FakeEntry]): FakeFilesystem =
    new FakeFilesystem(entries)

final case class ShellInvocation(command: String, options: ShellOptions)

final class ScriptedShellRunner(results: Vector[ShellExit], default: ShellExit = ShellExit.Completed(0)) extends ShellRunner:
  private val invocations0 = ArrayBuffer.empty[ShellInvocation]

  def invocations: Vector[ShellInvocation] = invocations0.toVector

  def run(command: String, options: ShellOptions): ShellExit =
    val index = invocations0.size
    invocations0 += ShellInvocation(command, options)
    results.lift(index).getOrElse(default)

object ScriptedShellRunner:
  def always(result: ShellExit): ScriptedShellRunner =
    ScriptedShellRunner(Vector.empty, result)

/**
 * A fixed environment for tests, so path expansion never depends on the machine running them.
 */
object TestEnvironment:
  val HomeDirectory: String = "/home/test"
  val WorkingDirectory: String = "/workspace"

  def apply(variables: Map[String, String] = Map.empty): Environment =
    Environment.fixed(variables, HomeDirectory, WorkingDirectory)

  /** A resolver over that fixed environment. */
  def resolver(variables: Map[String, String] = Map.empty): PathResolver =
    PathResolver(apply(variables))

final case class CapturedRuntime(ctx: RuntimeContext, output: ByteArrayOutputStream)

object TestRuntime:
  def apply(
    baseDirectory: String = "/workspace",
    options:       CoreOptions = CoreOptions(),
    fs:            Filesystem = FakeFilesystem.empty,
    shell:         ShellRunner = ScriptedShellRunner.always(ShellExit.Completed(0)),
    output:        ByteArrayOutputStream = ByteArrayOutputStream(),
    clock:         Clock = Clock.systemUTC(),
    paths:         PathResolver = TestEnvironment.resolver(),
  ): CapturedRuntime =
    CapturedRuntime(
      RuntimeContext(
        baseDirectory = baseDirectory,
        options = options,
        log = Logger(PrintStream(output), minimum = Level.Debug, color = false),
        fs = fs,
        shell = shell,
        clock = clock,
        paths = paths,
      ),
      output,
    )
