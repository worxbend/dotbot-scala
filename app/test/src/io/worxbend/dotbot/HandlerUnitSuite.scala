package io.worxbend.dotbot

import io.worxbend.dotbot.core.CleanOptions
import io.worxbend.dotbot.core.ShellEntryOptions
import io.worxbend.dotbot.core.CleanEntry
import io.worxbend.dotbot.core.CleanHandler
import io.worxbend.dotbot.core.CleanSpec
import io.worxbend.dotbot.core.CoreOptions
import io.worxbend.dotbot.core.CreateEntry
import io.worxbend.dotbot.core.CreateHandler
import io.worxbend.dotbot.core.CreateSpec
import io.worxbend.dotbot.core.FileMode
import io.worxbend.dotbot.core.LinkEntry
import io.worxbend.dotbot.core.LinkHandler
import io.worxbend.dotbot.core.LinkOptions
import io.worxbend.dotbot.core.LinkSpec
import io.worxbend.dotbot.core.LinkType
import io.worxbend.dotbot.core.Outcome
import io.worxbend.dotbot.core.PathUtil
import io.worxbend.dotbot.core.ShellEntry
import io.worxbend.dotbot.core.ShellExit
import io.worxbend.dotbot.core.ShellHandler
import io.worxbend.dotbot.core.ShellOptions
import io.worxbend.dotbot.core.ShellSpec
import io.worxbend.dotbot.testkit.FakeEntry
import io.worxbend.dotbot.testkit.FakeFilesystem
import io.worxbend.dotbot.testkit.ScriptedShellRunner
import io.worxbend.dotbot.testkit.TestRuntime

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class HandlerUnitSuite extends munit.FunSuite:
  test("create handler creates and chmods missing paths through the filesystem port") {
    val base    = "/workspace"
    val fs      = FakeFilesystem(Map(base -> FakeEntry.Directory))
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val path    = PathUtil.join(base, "generated")
    val spec    = CreateSpec(Vector(CreateEntry.Path("generated", FileMode.rwxAll)))

    assertEquals(CreateHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(fs.hasDirectory(path))
    assertEquals(fs.mkdirCalls, Vector(path))
    assertEquals(fs.chmodCalls, Vector(path -> FileMode.rwxAll))
    assertEquals(
      runtime.output.toString,
      s"""step  Creating path $path
         |info  All paths have been set up
         |""".stripMargin,
    )
  }

  test("clean handler removes broken links that point inside the base directory") {
    val base       = "/workspace"
    val dir        = PathUtil.join(base, "stale")
    val brokenLink = PathUtil.join(dir, "broken")
    val fs         = FakeFilesystem(
      Map(
        base       -> FakeEntry.Directory,
        dir        -> FakeEntry.Directory,
        brokenLink -> FakeEntry.Symlink("missing"),
      ),
    )
    val runtime    = TestRuntime(baseDirectory = base, fs = fs)
    val spec       = CleanSpec(Vector(CleanEntry.Target("stale", CleanOptions(force = false, recursive = false))))

    assertEquals(CleanHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(!fs.lexists(brokenLink))
    assertEquals(fs.removeCalls, Vector(brokenLink))
    assertEquals(
      runtime.output.toString,
      s"""step  Removing invalid link $brokenLink -> ${PathUtil.join(dir, "missing")}
         |info  All targets have been cleaned
         |""".stripMargin,
    )
  }

  test("clean handler leaves outside-base broken links unless force is enabled") {
    val base       = "/workspace"
    val dir        = PathUtil.join(base, "stale")
    val brokenLink = PathUtil.join(dir, "external")
    val fs         = FakeFilesystem(
      Map(
        base       -> FakeEntry.Directory,
        dir        -> FakeEntry.Directory,
        brokenLink -> FakeEntry.Symlink("/outside/missing"),
      ),
    )
    val runtime    = TestRuntime(baseDirectory = base, fs = fs)
    val spec       = CleanSpec(Vector(CleanEntry.Target("stale", CleanOptions(force = false, recursive = false))))

    assertEquals(CleanHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(fs.lexists(brokenLink))
    assertEquals(fs.removeCalls, Vector.empty)
    assertEquals(
      runtime.output.toString,
      s"""info  Link $brokenLink -> /outside/missing not removed.
         |info  All targets have been cleaned
         |""".stripMargin,
    )

    val forceRuntime = TestRuntime(baseDirectory = base, fs = fs)
    val forceSpec    = CleanSpec(Vector(CleanEntry.Target("stale", CleanOptions(force = true, recursive = false))))

    assertEquals(CleanHandler().execute(forceRuntime.ctx, forceSpec), Outcome.Ok)
    assert(!fs.lexists(brokenLink))
    assertEquals(fs.removeCalls, Vector(brokenLink))
  }

  test("clean handler recursively removes nested broken links") {
    val base       = "/workspace"
    val dir        = PathUtil.join(base, "stale")
    val nested     = PathUtil.join(dir, "nested")
    val brokenLink = PathUtil.join(nested, "broken")
    val fs         = FakeFilesystem(
      Map(
        base       -> FakeEntry.Directory,
        dir        -> FakeEntry.Directory,
        nested     -> FakeEntry.Directory,
        brokenLink -> FakeEntry.Symlink("missing"),
      ),
    )
    val runtime    = TestRuntime(baseDirectory = base, fs = fs)
    val spec       = CleanSpec(Vector(CleanEntry.Target("stale", CleanOptions(force = false, recursive = true))))

    assertEquals(CleanHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(!fs.lexists(brokenLink))
    assertEquals(fs.removeCalls, Vector(brokenLink))
    assertEquals(
      runtime.output.toString,
      s"""step  Removing invalid link $brokenLink -> ${PathUtil.join(nested, "missing")}
         |info  All targets have been cleaned
         |""".stripMargin,
    )
  }

  test("clean handler ignores nonexistent target directories") {
    val base    = "/workspace"
    val fs      = FakeFilesystem(Map(base -> FakeEntry.Directory))
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = CleanSpec(Vector(CleanEntry.Target("missing", CleanOptions(force = false, recursive = false))))

    assertEquals(CleanHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.removeCalls, Vector.empty)
    assertEquals(
      runtime.output.toString,
      """debug Ignoring nonexistent directory missing
        |info  All targets have been cleaned
        |""".stripMargin,
    )
  }

  test("shell handler passes typed options to the shell runner and reports failures") {
    val base    = "/workspace"
    val timeout = Duration.ofSeconds(3)
    val shell   = ScriptedShellRunner(Vector(ShellExit.Completed(7)))
    val runtime = TestRuntime(
      baseDirectory = base,
      options = CoreOptions(verbose = 2, shellTimeout = timeout),
      shell = shell,
    )
    val spec    = ShellSpec(
      Vector(
        ShellEntry.Command(
          command = "echo hi",
          description = "Say hello",
          options = ShellEntryOptions(quiet = false, stdin = true, stdout = false, stderr = true),
        ),
      ),
    )

    assertEquals(ShellHandler().execute(runtime.ctx, spec), Outcome.Failed)
    assertEquals(
      shell.invocations.map(invocation => invocation.command -> invocation.options),
      Vector(
        "echo hi" -> ShellOptions(
          cwd = base,
          enableStdin = true,
          enableStdout = true,
          enableStderr = true,
          timeout = timeout,
        ),
      ),
    )
    assertEquals(
      runtime.output.toString,
      """step  Say hello [echo hi]
        |warn  Command [echo hi] failed
        |error Some commands were not successfully executed
        |""".stripMargin,
    )
  }

  test("shell handler logs quiet descriptions without echoing command text") {
    val base    = "/workspace"
    val shell   = ScriptedShellRunner(Vector(ShellExit.Completed(0)))
    val runtime = TestRuntime(baseDirectory = base, shell = shell)
    val spec    = ShellSpec(
      Vector(
        ShellEntry.Command(
          command = "secret command",
          description = "Quiet setup",
          options = ShellEntryOptions(quiet = true, stdin = false, stdout = false, stderr = false),
        ),
      ),
    )

    assertEquals(ShellHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(shell.invocations.map(_.command), Vector("secret command"))
    assertEquals(
      runtime.output.toString,
      """info  Quiet setup
        |info  All commands have been executed
        |""".stripMargin,
    )
  }

  test("shell handler treats timed out commands as failed executions") {
    val base    = "/workspace"
    val timeout = Duration.ofMillis(25)
    val shell   = ScriptedShellRunner(Vector(ShellExit.TimedOut))
    val runtime = TestRuntime(baseDirectory = base, options = CoreOptions(shellTimeout = timeout), shell = shell)
    val spec    = ShellSpec(
      Vector(
        ShellEntry.Command(
          command = "sleep 5",
          description = "",
          options = ShellEntryOptions(quiet = false, stdin = false, stdout = true, stderr = false),
        ),
      ),
    )

    assertEquals(ShellHandler().execute(runtime.ctx, spec), Outcome.Failed)
    assertEquals(
      shell.invocations.map(invocation => invocation.command -> invocation.options),
      Vector(
        "sleep 5" -> ShellOptions(
          cwd = base,
          enableStdin = false,
          enableStdout = true,
          enableStderr = false,
          timeout = timeout,
        ),
      ),
    )
    assertEquals(
      runtime.output.toString,
      """step  sleep 5
        |warn  Command [sleep 5] failed
        |error Some commands were not successfully executed
        |""".stripMargin,
    )
  }

  test("link handler creates symlinks through the filesystem port") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val link    = PathUtil.join(base, "linked.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", LinkOptions())))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.readlink(link), Right(target))
    assertEquals(
      runtime.output.toString,
      s"""step  Creating symlink linked.txt -> $target
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler creates missing parent directories when requested") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val parent  = PathUtil.join(base, "nested")
    val link    = PathUtil.join(parent, "linked.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("nested/linked.txt", "source.txt", LinkOptions(create = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.mkdirCalls, Vector(parent))
    assertEquals(fs.readlink(link), Right(target))
    assertEquals(
      runtime.output.toString,
      s"""step  Creating directory $parent
         |step  Creating symlink nested/linked.txt -> $target
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler reports nonexistent targets without ignore-missing") {
    val base    = "/workspace"
    val fs      = FakeFilesystem(Map(base -> FakeEntry.Directory))
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "missing.txt", LinkOptions())))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Failed)
    assert(!fs.lexists(PathUtil.join(base, "linked.txt")))
    assertEquals(
      runtime.output.toString,
      """warn  Nonexistent target linked.txt -> missing.txt
         |error Some links were not successfully set up
         |""".stripMargin,
    )
  }

  test("link handler accepts an existing symlink that already points at the target") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val link    = PathUtil.join(base, "linked.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
        link   -> FakeEntry.Symlink(target),
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", LinkOptions())))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.removeCalls, Vector.empty)
    assertEquals(
      runtime.output.toString,
      s"""info  Link exists linked.txt -> $target
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler can create hardlinks through the filesystem port") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val link    = PathUtil.join(base, "linked.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val options = LinkOptions(linkType = LinkType.Hardlink)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", options)))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(fs.lexists(link))
    assert(!fs.isSymlink(link))
    assertEquals(
      runtime.output.toString,
      s"""step  Creating hardlink linked.txt -> $target
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler skips links when the conditional command fails") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val link    = PathUtil.join(base, "linked.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
      ),
    )
    val shell   = ScriptedShellRunner(Vector(ShellExit.Completed(1)))
    val runtime = TestRuntime(baseDirectory = base, fs = fs, shell = shell)
    val spec    =
      LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", LinkOptions(ifCommand = "test -f source.txt"))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(!fs.lexists(link))
    assertEquals(shell.invocations.map(_.command), Vector("test -f source.txt"))
    assertEquals(
      runtime.output.toString,
      """info  Skipping linked.txt
        |info  All links have been set up
        |""".stripMargin,
    )
  }

  test("link handler expands a glob through the filesystem port") {
    val base    = "/workspace"
    val fs      = FakeFilesystem(
      Map(
        base                                   -> FakeEntry.Directory,
        PathUtil.join(base, "source")          -> FakeEntry.Directory,
        PathUtil.join(base, "source", "a.txt") -> FakeEntry.File,
        PathUtil.join(base, "source", "b.txt") -> FakeEntry.File,
        PathUtil.join(base, "dest")            -> FakeEntry.Directory,
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("dest", "source/*", LinkOptions(glob = true))))

    // Globbing used to call `java.nio.file.Files` directly from core, so it reached the real disk
    // whatever filesystem was injected and this test could not be written at all.
    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(fs.isSymlink(PathUtil.join(base, "dest", "source", "a.txt")))
    assert(fs.isSymlink(PathUtil.join(base, "dest", "source", "b.txt")))
    assertEquals(
      runtime.output.toString,
      """debug Globs from 'source/*': Vector("/workspace/source/a.txt", "/workspace/source/b.txt")
        |step  Creating symlink dest/source/a.txt -> /workspace/source/a.txt
        |step  Creating symlink dest/source/b.txt -> /workspace/source/b.txt
        |info  All links have been set up
        |""".stripMargin,
    )
  }

  test("link handler does not run the conditional command during a dry run") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
      ),
    )
    val shell   = ScriptedShellRunner(Vector.empty)
    val runtime = TestRuntime(baseDirectory = base, options = CoreOptions(dryRun = true), fs = fs, shell = shell)
    val spec    =
      LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", LinkOptions(ifCommand = "touch marker"))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    // The point of the test: an `if` command is a side effect, so `--dry-run` must report it
    // rather than run it, and must still plan the link it guards.
    assertEquals(shell.invocations, Vector.empty)
    assertEquals(
      runtime.output.toString,
      """step  Would check condition [touch marker]
        |step  Would create symlink linked.txt -> /workspace/source.txt
        |info  All links have been set up
        |""".stripMargin,
    )
  }

  test("link handler can create links for missing targets when ignore-missing is enabled") {
    val base    = "/workspace"
    val link    = PathUtil.join(base, "linked.txt")
    val fs      = FakeFilesystem(Map(base -> FakeEntry.Directory))
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "missing.txt", LinkOptions(ignoreMissing = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.readlink(link), Right(PathUtil.join(base, "missing.txt")))
    assertEquals(
      runtime.output.toString,
      s"""step  Creating symlink linked.txt -> ${PathUtil.join(base, "missing.txt")}
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler relinks symlinks that point at the wrong target") {
    val base      = "/workspace"
    val target    = PathUtil.join(base, "source.txt")
    val link      = PathUtil.join(base, "linked.txt")
    val oldTarget = PathUtil.join(base, "old.txt")
    val fs        = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
        link   -> FakeEntry.Symlink(oldTarget),
      ),
    )
    val runtime   = TestRuntime(baseDirectory = base, fs = fs)
    val spec      = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", LinkOptions(relink = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.removeCalls, Vector(link))
    assertEquals(fs.readlink(link), Right(target))
    assertEquals(
      runtime.output.toString,
      s"""step  Removing linked.txt
         |step  Creating symlink linked.txt -> $target
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler can force a symlink over an existing directory") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val link    = PathUtil.join(base, "linked-dir")
    val child   = PathUtil.join(link, "child.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
        link   -> FakeEntry.Directory,
        child  -> FakeEntry.File,
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked-dir", "source.txt", LinkOptions(force = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assertEquals(fs.removeCalls, Vector(link))
    assert(!fs.lexists(child))
    assertEquals(fs.readlink(link), Right(target))
    assertEquals(
      runtime.output.toString,
      s"""step  Removing linked-dir
         |step  Creating symlink linked-dir -> $target
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler backs up existing files with the injected clock timestamp") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val link    = PathUtil.join(base, "linked.txt")
    val backup  = PathUtil.join(base, "linked.txt.dotbot-backup.20200102-030405")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
        link   -> FakeEntry.File,
      ),
    )
    val clock   = Clock.fixed(Instant.parse("2020-01-02T03:04:05Z"), ZoneOffset.UTC)
    val runtime = TestRuntime(baseDirectory = base, fs = fs, clock = clock)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", LinkOptions(backup = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Ok)
    assert(fs.paths.contains(backup))
    assertEquals(fs.readlink(link), Right(target))
    assertEquals(
      runtime.output.toString,
      s"""step  Backed up file linked.txt to linked.txt.dotbot-backup.20200102-030405
         |step  Creating symlink linked.txt -> $target
         |info  All links have been set up
         |""".stripMargin,
    )
  }

  test("link handler refuses to replace a path that is the same file as the target") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("source.txt", "source.txt", LinkOptions(force = true))))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Failed)
    assertEquals(fs.removeCalls, Vector.empty)
    assertEquals(
      runtime.output.toString,
      s"""warn  source.txt appears to be the same file as $target.
         |warn  source.txt already exists but is a regular file or directory
         |error Some links were not successfully set up
         |""".stripMargin,
    )
  }

  test("link handler rejects hardlinks when the destination is already a symlink") {
    val base    = "/workspace"
    val target  = PathUtil.join(base, "source.txt")
    val link    = PathUtil.join(base, "linked.txt")
    val fs      = FakeFilesystem(
      Map(
        base   -> FakeEntry.Directory,
        target -> FakeEntry.File,
        link   -> FakeEntry.Symlink(target),
      ),
    )
    val runtime = TestRuntime(baseDirectory = base, fs = fs)
    val options = LinkOptions(linkType = LinkType.Hardlink)
    val spec    = LinkSpec(None, Vector(LinkEntry.Link("linked.txt", "source.txt", options)))

    assertEquals(LinkHandler().execute(runtime.ctx, spec), Outcome.Failed)
    assertEquals(fs.readlink(link), Right(target))
    assertEquals(
      runtime.output.toString,
      """warn  linked.txt already exists but is a symbolic link, not a hard link
        |error Some links were not successfully set up
        |""".stripMargin,
    )
  }
