package io.worxbend.dotbot

import io.worxbend.dotbot.core.CleanEntry
import io.worxbend.dotbot.core.CleanHandler
import io.worxbend.dotbot.core.CleanSpec
import io.worxbend.dotbot.core.CoreOptions
import io.worxbend.dotbot.core.LinkEntry
import io.worxbend.dotbot.core.LinkHandler
import io.worxbend.dotbot.core.LinkOptions
import io.worxbend.dotbot.core.LinkSpec
import io.worxbend.dotbot.core.Outcome
import io.worxbend.dotbot.testkit.FakeEntry
import io.worxbend.dotbot.testkit.FakeFilesystem
import io.worxbend.dotbot.testkit.TestEnvironment
import io.worxbend.dotbot.testkit.TestRuntime

/**
 * Each test here pins a specific defect so it cannot come back. They are kept apart from
 * `HandlerUnitSuite`, which describes what the handlers are for, because these describe what
 * they must never do again.
 */
class HandlerRegressionSuite extends munit.FunSuite:
  private val base = "/workspace"

  test("a link with a missing target does not create its parent directory") {
    val fs = FakeFilesystem(Map(base -> FakeEntry.Directory))
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec = LinkSpec(
      None,
      Vector(LinkEntry.Link("nested/deep/config", "missing-source.conf", LinkOptions(create = true))),
    )

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Failed)

    // The run failed, so it must not have changed anything. Creating the parent first left an
    // empty directory tree behind that neither a re-run nor --dry-run ever predicted.
    assertEquals(fs.mkdirCalls, Vector.empty)
    assert(!fs.lexists(s"$base/nested/deep"))
  }

  test("a link with a present target still creates its parent directory") {
    val fs = FakeFilesystem(
      Map(base -> FakeEntry.Directory, s"$base/source.conf" -> FakeEntry.File),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec = LinkSpec(None, Vector(LinkEntry.Link("nested/config", "source.conf", LinkOptions(create = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.mkdirCalls.map(_._1), Vector(s"$base/nested"))
  }

  test("a directory named like a variable is not expanded during a recursive clean") {
    val fs = FakeFilesystem(
      Map(
        base -> FakeEntry.Directory,
        s"$base/$$cache" -> FakeEntry.Directory,
        s"$base/$$cache/broken" -> FakeEntry.Symlink(s"$base/gone"),
      ),
    )
    val runtime = TestRuntime(
      baseDirectory = base,
      fs = fs,
      // If the entry name were expanded again, this value would be substituted for it.
      paths = TestEnvironment.resolver(Map("cache" -> "/somewhere/else")),
    )
    val spec = CleanSpec(Vector(CleanEntry.Target(base, force = false, recursive = true)))

    assertEquals(CleanHandler().execute(runtime.ctx, spec), Outcome.Ok)

    // The broken link inside the oddly named directory is found and removed, which only happens
    // if the directory was walked under its real name.
    assertEquals(fs.removeCalls, Vector(s"$base/$$cache/broken"))
  }

  test("an unset variable in a clean target does not turn it into the filesystem root") {
    val fs = FakeFilesystem(Map(base -> FakeEntry.Directory, "/" -> FakeEntry.Directory))
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec = CleanSpec(Vector(CleanEntry.Target("$XDG_CONFIG_HOME", force = true, recursive = true)))

    assertEquals(CleanHandler().execute(runtime.ctx, spec), Outcome.Ok)

    // Expanding the unset variable to "" used to resolve this to "/" and walk the whole
    // filesystem with force enabled. It must resolve under the base directory instead.
    assertEquals(fs.removeCalls, Vector.empty)
  }

  test("dry run reports the link it would create without touching the filesystem") {
    val fs = FakeFilesystem(Map(base -> FakeEntry.Directory, s"$base/source.conf" -> FakeEntry.File))
    val runtime = TestRuntime(
      baseDirectory = base,
      options = CoreOptions(dryRun = true),
      fs = fs,
    )
    val spec = LinkSpec(None, Vector(LinkEntry.Link("nested/config", "source.conf", LinkOptions(create = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.mkdirCalls, Vector.empty)
    assert(!fs.lexists(s"$base/nested/config"))
    assert(runtime.output.toString.contains("Would create directory"), runtime.output.toString)
  }
