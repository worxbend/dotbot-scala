package io.worxbend.dotbot.fs

import io.worxbend.dotbot.core.FileMode
import io.worxbend.dotbot.core.Filesystem as CoreFilesystem

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

import scala.jdk.CollectionConverters.*
import scala.util.Try

type Filesystem = CoreFilesystem

object OsFilesystem extends Filesystem:
  def exists(path: String): Boolean =
    Files.exists(Paths.get(path))

  def lexists(path: String): Boolean =
    Files.exists(Paths.get(path), LinkOption.NOFOLLOW_LINKS)

  def isDir(path: String): Boolean =
    Files.isDirectory(Paths.get(path))

  def isSymlink(path: String): Boolean =
    Files.isSymbolicLink(Paths.get(path))

  def listDir(path: String): Either[Throwable, Vector[String]] =
    Try:
      val stream = Files.list(Paths.get(path))
      try stream.iterator().asScala.map(_.getFileName.toString).toVector
      finally stream.close()
    .toEither

  def walk(root: String, maxDepth: Int): Either[Throwable, Vector[String]] =
    Try:
      if !Files.exists(Paths.get(root)) then Vector.empty
      else
        val stream = Files.walk(Paths.get(root), maxDepth)
        try stream.iterator().asScala.map(_.normalize().toString).toVector
        finally stream.close()
    .toEither

  def mkdirAll(path: String): Either[Throwable, Unit] =
    Try:
      Files.createDirectories(Paths.get(path))
      ()
    .toEither

  def chmod(path: String, mode: FileMode): Either[Throwable, Unit] =
    Try:
      Files.setPosixFilePermissions(Paths.get(path), permissionsFromMode(mode).asJava)
      ()
    .toEither

  def readlink(path: String): Either[Throwable, String] =
    Try(Files.readSymbolicLink(Paths.get(path)).toString).toEither

  def realpath(path: String): Either[Throwable, String] =
    Try(Paths.get(path).toRealPath().toString).toEither

  def remove(path: String): Either[Throwable, Unit] =
    Try:
      Files.delete(Paths.get(path))
      ()
    .toEither

  def removeAll(path: String): Either[Throwable, Unit] =
    Try:
      os.remove.all(os.Path(path, os.pwd))
      ()
    .toEither

  def rename(from: String, to: String): Either[Throwable, Unit] =
    Try:
      Files.move(Paths.get(from), Paths.get(to), StandardCopyOption.REPLACE_EXISTING)
      ()
    .toEither

  def sameFile(a: String, b: String): Either[Throwable, Boolean] =
    Try(Files.isSameFile(Paths.get(a), Paths.get(b))).toEither

  def stat(path: String): Either[Throwable, Unit] =
    Try:
      Files.readAttributes(Paths.get(path), classOf[java.nio.file.attribute.BasicFileAttributes])
      ()
    .toEither

  def symlink(target: String, link: String): Either[Throwable, Unit] =
    Try:
      Files.createSymbolicLink(Paths.get(link), Paths.get(target))
      ()
    .toEither

  def hardlink(target: String, link: String): Either[Throwable, Unit] =
    Try:
      Files.createLink(Paths.get(link), Paths.get(target))
      ()
    .toEither

  private[fs] def permissionsFromMode(mode: FileMode): Set[PosixFilePermission] =
    PermissionBits.collect {
      case (bit, permission) if (mode.value & bit) != 0 => permission
    }.toSet

  private val PermissionBits: Vector[(Int, PosixFilePermission)] =
    Vector(
      FileMode.ownerRead.value -> PosixFilePermission.OWNER_READ,
      FileMode.ownerWrite.value -> PosixFilePermission.OWNER_WRITE,
      FileMode.ownerExecute.value -> PosixFilePermission.OWNER_EXECUTE,
      FileMode.groupRead.value -> PosixFilePermission.GROUP_READ,
      FileMode.groupWrite.value -> PosixFilePermission.GROUP_WRITE,
      FileMode.groupExecute.value -> PosixFilePermission.GROUP_EXECUTE,
      FileMode.othersRead.value -> PosixFilePermission.OTHERS_READ,
      FileMode.othersWrite.value -> PosixFilePermission.OTHERS_WRITE,
      FileMode.othersExecute.value -> PosixFilePermission.OTHERS_EXECUTE,
    )
