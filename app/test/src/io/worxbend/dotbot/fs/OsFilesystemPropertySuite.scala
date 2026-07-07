package io.worxbend.dotbot.fs

import io.worxbend.dotbot.core.FileMode
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class OsFilesystemPropertySuite extends munit.ScalaCheckSuite:
  private val Mode: Gen[Int] =
    Gen.choose(0, FileMode.rwxAll.value)

  property("permissionsFromMode maps each low 9-bit mode directly to POSIX permissions") {
    forAll(Mode) { mode =>
      assertEquals(OsFilesystem.permissionsFromMode(FileMode(mode)), OsFilesystemPropertySuite.expectedPermissions(mode))
    }
  }

object OsFilesystemPropertySuite:
  import java.nio.file.attribute.PosixFilePermission

  private def expectedPermissions(mode: Int): Set[PosixFilePermission] =
    PermissionBits.collect {
      case (bit, permission) if (mode & bit) != 0 => permission
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
