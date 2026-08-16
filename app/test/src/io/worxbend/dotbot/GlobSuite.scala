package io.worxbend.dotbot

import io.worxbend.dotbot.core.Glob
import io.worxbend.dotbot.fs.OsFilesystem

class GlobSuite extends munit.FunSuite:
  test("hasGlobChars recognizes shell glob metacharacters") {
    assert(!Glob.hasGlobChars("plain/path"))
    assert(Glob.hasGlobChars("plain/*.txt"))
    assert(Glob.hasGlobChars("plain/file?.txt"))
    assert(Glob.hasGlobChars("plain/[ab].txt"))
  }

  test("double-star glob without trailing slash excludes directories") {
    val root = os.temp.dir(prefix = "dotbot-scala-glob-double-star-")
    os.makeDir.all(root / "one" / "nested")
    os.write(root / "one" / "nested" / "file.txt", "content\n")

    val matches = Glob.glob(OsFilesystem, s"$root/**").fold(error => fail(error.render), identity)

    assertEquals(matches, Vector((root / "one" / "nested" / "file.txt").toString))
  }

  test("createGlobResults applies excludes and sorts results") {
    val root = os.temp.dir(prefix = "dotbot-scala-glob-exclude-")
    os.write(root / "b.txt", "b\n")
    os.write(root / "a.txt", "a\n")
    os.write(root / "skip.txt", "skip\n")

    val matches = Glob.createGlobResults(OsFilesystem, s"$root/*.txt", Vector(s"$root/skip.txt")).fold(error => fail(error.render), identity)

    assertEquals(matches, Vector((root / "a.txt").toString, (root / "b.txt").toString))
  }

  test("globLinkItem uses common-prefix stripping for double-star patterns") {
    val root = os.temp.dir(prefix = "dotbot-scala-glob-link-item-")
    val pattern = s"$root/source/**/*.txt"
    val item = (root / "source" / "nested" / "file.txt").toString

    assertEquals(Glob.globLinkItem(pattern, item), "source/nested/file.txt")
  }
