package io.worxbend.dotbot

import io.worxbend.dotbot.core.Glob
import io.worxbend.dotbot.testkit.FakeEntry
import io.worxbend.dotbot.testkit.FakeFilesystem

class GlobTraversalSuite extends munit.FunSuite:
  test("excludes filter the included paths without extra filesystem walks") {
    val fs = FakeFilesystem(
      Map(
        "/source"        -> FakeEntry.Directory,
        "/source/a.conf" -> FakeEntry.File,
        "/source/b.conf" -> FakeEntry.File,
        "/source/nested" -> FakeEntry.Directory,
      ),
    )
    assertEquals(
      Glob.createGlobResults(fs, "/source/*", Vector("/source/a.*", "/source/no-match*")),
      Right(Vector("/source/b.conf", "/source/nested")),
    )
    assertEquals(fs.walkCalls, Vector("/source" -> 1))
  }

  test("invalid exclusions fail before walking the filesystem") {
    val fs = FakeFilesystem.empty
    assert(Glob.createGlobResults(fs, "/source/*", Vector("/source/bad[")).isLeft)
    assertEquals(fs.walkCalls, Vector.empty)
  }

  test("double-star exclusion retains the existing directory filtering rule") {
    val fs = FakeFilesystem(
      Map(
        "/source"        -> FakeEntry.Directory,
        "/source/nested" -> FakeEntry.Directory,
        "/source/file"   -> FakeEntry.File,
      ),
    )
    assertEquals(Glob.createGlobResults(fs, "/source/*", Vector("/source/**")), Right(Vector("/source/nested")))
  }
