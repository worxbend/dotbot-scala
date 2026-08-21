package io.worxbend.dotbot.fs

import io.worxbend.dotbot.core.FileMode
import io.worxbend.dotbot.core.Filesystem
import io.worxbend.dotbot.core.FsFailure
import io.worxbend.dotbot.core.FsResult

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

import scala.jdk.CollectionConverters.*
import scala.util.Try

object OsFilesystem extends Filesystem:
  def exists(path: String): Boolean =
    Files.exists(Paths.get(path))

  def lexists(path: String): Boolean =
    Files.exists(Paths.get(path), LinkOption.NOFOLLOW_LINKS)

  def isDir(path: String): Boolean =
    Files.isDirectory(Paths.get(path))

  def isSymlink(path: String): Boolean =
    Files.isSymbolicLink(Paths.get(path))

  def listDir(path: String): FsResult[Vector[String]] =
    Try:
      val stream = Files.list(Paths.get(path))
      try stream.iterator().asScala.map(_.getFileName.toString).toVector
      finally stream.close()
    .toEither.left.map(asFailure)

  def walk(root: String, maxDepth: Int): FsResult[Vector[String]] =
    Try:
      if !Files.exists(Paths.get(root)) then Vector.empty
      else
        val stream = Files.walk(Paths.get(root), maxDepth)
        try stream.iterator().asScala.map(_.normalize().toString).toVector
        finally stream.close()
    .toEither.left.map(asFailure)

  def mkdirAll(path: String): FsResult[Unit] =
    Try:
      Files.createDirectories(Paths.get(path))
      ()
    .toEither.left.map(asFailure)

  def chmod(path: String, mode: FileMode): FsResult[Unit] =
    Try:
      Files.setPosixFilePermissions(Paths.get(path), permissionsFromMode(mode).asJava)
      ()
    .toEither.left.map(asFailure)

  def readlink(path: String): FsResult[String] =
    Try(Files.readSymbolicLink(Paths.get(path)).toString).toEither.left.map(asFailure)

  def realpath(path: String): FsResult[String] =
    Try(Paths.get(path).toRealPath().toString).toEither.left.map(asFailure)

  def remove(path: String): FsResult[Unit] =
    Try:
      Files.delete(Paths.get(path))
      ()
    .toEither.left.map(asFailure)

  def removeAll(path: String): FsResult[Unit] =
    Try:
      os.remove.all(os.Path(path, os.pwd))
      ()
    .toEither.left.map(asFailure)

  def rename(from: String, to: String): FsResult[Unit] =
    Try:
      Files.move(Paths.get(from), Paths.get(to), StandardCopyOption.REPLACE_EXISTING)
      ()
    .toEither.left.map(asFailure)

  def sameFile(a: String, b: String): FsResult[Boolean] =
    Try(Files.isSameFile(Paths.get(a), Paths.get(b))).toEither.left.map(asFailure)

  def stat(path: String): FsResult[Unit] =
    Try:
      Files.readAttributes(Paths.get(path), classOf[java.nio.file.attribute.BasicFileAttributes])
      ()
    .toEither.left.map(asFailure)

  def symlink(target: String, link: String): FsResult[Unit] =
    Try:
      Files.createSymbolicLink(Paths.get(link), Paths.get(target))
      ()
    .toEither.left.map(asFailure)

  def hardlink(target: String, link: String): FsResult[Unit] =
    Try:
      Files.createLink(Paths.get(link), Paths.get(target))
      ()
    .toEither.left.map(asFailure)

  /**
   * Reduce a thrown exception to the message the log will show.
   *
   * `String.valueOf` rather than `.getMessage` directly: an exception is allowed to carry a null
   * message, and this keeps that rendering as the text "null" -- which is what string
   * interpolation of the raw message produced before, so no log line changes.
   */
  private def asFailure(error: Throwable): FsFailure =
    FsFailure(String.valueOf(error.getMessage))

  private[fs] def permissionsFromMode(mode: FileMode): Set[PosixFilePermission] =
    PermissionBits.collect {
      case (bit, permission) if (mode.value & bit) != 0 => permission
    }.toSet

  private val PermissionBits: Vector[(Int, PosixFilePermission)] =
    Vector(
      FileMode.ownerRead.value     -> PosixFilePermission.OWNER_READ,
      FileMode.ownerWrite.value    -> PosixFilePermission.OWNER_WRITE,
      FileMode.ownerExecute.value  -> PosixFilePermission.OWNER_EXECUTE,
      FileMode.groupRead.value     -> PosixFilePermission.GROUP_READ,
      FileMode.groupWrite.value    -> PosixFilePermission.GROUP_WRITE,
      FileMode.groupExecute.value  -> PosixFilePermission.GROUP_EXECUTE,
      FileMode.othersRead.value    -> PosixFilePermission.OTHERS_READ,
      FileMode.othersWrite.value   -> PosixFilePermission.OTHERS_WRITE,
      FileMode.othersExecute.value -> PosixFilePermission.OTHERS_EXECUTE,
    )
