package io.worxbend.dotbot.fs

import io.worxbend.dotbot.core.FileMode

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

import scala.jdk.CollectionConverters.*

class OsFilesystemSuite extends munit.FunSuite:
  test("permissionsFromMode matches POSIX rwx strings for all low 9-bit modes") {
    for mode <- 0 to FileMode.rwxAll.value do
      assertEquals(
        OsFilesystem.permissionsFromMode(FileMode(mode)),
        permissionsViaString(mode),
        clue = s"mode ${mode.toOctalString}",
      )
  }

  test("permissionsFromMode ignores non-permission bits") {
    assertEquals(
      OsFilesystem.permissionsFromMode(FileMode.rwxAll),
      OsFilesystem.permissionsFromMode(FileMode(FileMode.rwxAll.value | (1 << 12))),
    )
  }

  private def permissionsViaString(mode: Int): Set[PosixFilePermission] =
    val chars =
      OsFilesystemSuite.PermissionChars.map { case (bit, enabled) =>
        if hasBit(mode, bit) then enabled else '-'
      }.mkString
    PosixFilePermissions.fromString(chars).asScala.toSet

  private def hasBit(mode: Int, bit: FileMode): Boolean =
    (mode & bit.value) != 0

object OsFilesystemSuite:
  private val PermissionChars: Vector[(FileMode, Char)] =
    Vector(
      FileMode.ownerRead     -> 'r',
      FileMode.ownerWrite    -> 'w',
      FileMode.ownerExecute  -> 'x',
      FileMode.groupRead     -> 'r',
      FileMode.groupWrite    -> 'w',
      FileMode.groupExecute  -> 'x',
      FileMode.othersRead    -> 'r',
      FileMode.othersWrite   -> 'w',
      FileMode.othersExecute -> 'x',
    )
