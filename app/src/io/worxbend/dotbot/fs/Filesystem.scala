package io.worxbend.dotbot.fs

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

import scala.jdk.CollectionConverters.*
import scala.util.Try

trait Filesystem:
  def exists(path: String): Boolean
  def lexists(path: String): Boolean
  def isDir(path: String): Boolean
  def isSymlink(path: String): Boolean
  def listDir(path: String): Either[Throwable, Vector[String]]
  def mkdirAll(path: String, mode: Int): Either[Throwable, Unit]
  def chmod(path: String, mode: Int): Either[Throwable, Unit]
  def readlink(path: String): Either[Throwable, String]
  def realpath(path: String): Either[Throwable, String]
  def remove(path: String): Either[Throwable, Unit]
  def removeAll(path: String): Either[Throwable, Unit]
  def rename(from: String, to: String): Either[Throwable, Unit]
  def sameFile(a: String, b: String): Either[Throwable, Boolean]
  def stat(path: String): Either[Throwable, Unit]
  def symlink(target: String, link: String): Either[Throwable, Unit]
  def hardlink(target: String, link: String): Either[Throwable, Unit]

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

  def mkdirAll(path: String, mode: Int): Either[Throwable, Unit] =
    Try:
      Files.createDirectories(Paths.get(path))
      ()
    .toEither

  def chmod(path: String, mode: Int): Either[Throwable, Unit] =
    Try:
      val permissions = permissionsFromMode(mode)
      if permissions.nonEmpty then Files.setPosixFilePermissions(Paths.get(path), permissions.get.asJava)
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

  private def permissionsFromMode(mode: Int): Option[Set[PosixFilePermission]] =
    val ownerRead = (mode & 0x100) != 0
    val ownerWrite = (mode & 0x80) != 0
    val ownerExecute = (mode & 0x40) != 0
    val groupRead = (mode & 0x20) != 0
    val groupWrite = (mode & 0x10) != 0
    val groupExecute = (mode & 0x08) != 0
    val otherRead = (mode & 0x04) != 0
    val otherWrite = (mode & 0x02) != 0
    val otherExecute = (mode & 0x01) != 0
    val chars = StringBuilder()
    chars.append(if ownerRead then 'r' else '-')
    chars.append(if ownerWrite then 'w' else '-')
    chars.append(if ownerExecute then 'x' else '-')
    chars.append(if groupRead then 'r' else '-')
    chars.append(if groupWrite then 'w' else '-')
    chars.append(if groupExecute then 'x' else '-')
    chars.append(if otherRead then 'r' else '-')
    chars.append(if otherWrite then 'w' else '-')
    chars.append(if otherExecute then 'x' else '-')
    Try(PosixFilePermissions.fromString(chars.result()).asScala.toSet).toOption
