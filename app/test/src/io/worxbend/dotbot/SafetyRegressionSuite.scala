package io.worxbend.dotbot

import io.worxbend.dotbot.app.AppDependencies
import io.worxbend.dotbot.app.AppOptions
import io.worxbend.dotbot.app.DotbotApp
import io.worxbend.dotbot.app.RunMode
import io.worxbend.dotbot.core.Glob
import io.worxbend.dotbot.fs.OsFilesystem
import io.worxbend.dotbot.logging.TerminalCapabilities

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SafetyRegressionSuite extends munit.FunSuite:
  private val clock = Clock.fixed(Instant.parse("2020-01-02T03:04:05Z"), ZoneOffset.UTC)

  test("a regular file cannot be used as the base directory") {
    withRoot { root =>
      val config = root / "empty.yaml"
      os.write(config, "[]")
      val output = ByteArrayOutputStream()
      val stream = PrintStream(output)
      try
        val code = DotbotApp.run(
          AppOptions(configFiles = Vector(config.toString), baseDirectory = config.toString),
          stream,
          stderr = stream,
        )
        assertEquals(code, 1)
        assert(output.toString.contains("not a directory"))
      finally stream.close()
    }
  }

  test("an invalid path fails its action without throwing out of the application") {
    withRoot { root =>
      val config    = ujson.write(
        ujson.Arr(
          ujson.Obj("create" -> ujson.Arr("bad\u0000path")),
          ujson.Obj("create" -> ujson.Arr("after")),
        ),
      )
      val (code, _) = run(root, config)
      assertEquals(code, 1)
      assert(os.isDir(root / "after"))
    }
  }

  private def withRoot(test: os.Path => Unit): Unit =
    val root = os.temp.dir(prefix = "dotbot-safety-")
    try test(root)
    finally os.remove.all(root)

  private def run(
      root: os.Path,
      config: String,
      dryRun: Boolean = false,
      mode: RunMode = RunMode.Apply,
  ): (Int, String) =
    val file   = root / (if config.startsWith("[") then "install.json" else "install.yaml")
    os.write.over(file, config)
    val output = ByteArrayOutputStream()
    val stream = PrintStream(output)
    try
      val code = DotbotApp.run(
        AppOptions(configFiles = Vector(file.toString), dryRun = dryRun, mode = mode),
        stream,
        AppDependencies(clock = clock, capabilities = TerminalCapabilities(color = false, symbols = false)),
        stream,
      )
      code -> output.toString
    finally stream.close()

  test("a failed backup prevents forced deletion of the original") {
    withRoot { root =>
      val name           = "d" * 240 // Valid filename; appending the backup suffix exceeds NAME_MAX.
      os.write(root / name, "original")
      os.write(root / "source", "replacement")
      val (code, output) = run(root, s"- link:\n    $name: {path: source, backup: true, force: true}\n")

      assertEquals(code, 1)
      assert(output.contains("Failed to backup"), output)
      assert(!Files.isSymbolicLink((root / name).toNIO))
      assertEquals(os.read(root / name), "original")
    }
  }

  test("a colliding backup name never overwrites an earlier backup") {
    withRoot { root =>
      os.write(root / "dest", "original")
      os.write(root / "source", "replacement")
      val backup    = root / "dest.dotbot-backup.20200102-030405"
      os.write(backup, "earlier backup")
      val (code, _) = run(root, "- link:\n    dest: {path: source, backup: true, force: true}\n")

      assertEquals(code, 1)
      assertEquals(os.read(backup), "earlier backup")
      assertEquals(os.read(root / "dest"), "original")
    }
  }

  test("backup cannot move the link source out of its own path") {
    withRoot { root =>
      os.write(root / "source", "original")
      val (code, _) = run(root, "- link:\n    source: {path: source, backup: true}\n")

      assertEquals(code, 1)
      assertEquals(os.read(root / "source"), "original")
      assert(!os.exists(root / "source.dotbot-backup.20200102-030405"))
    }
  }

  test("force cannot delete the directory containing its source") {
    withRoot { root =>
      os.write(root / "dest" / "source", "original", createFolders = true)
      val (code, _) = run(root, "- link:\n    dest: {path: dest/source, force: true}\n")

      assertEquals(code, 1)
      assertEquals(os.read(root / "dest" / "source"), "original")
    }
  }

  test("relink without force reports the same regular-file conflict in dry-run and apply") {
    withRoot { root =>
      os.write(root / "dest", "original")
      os.write(root / "source", "replacement")
      val config                 = "- link:\n    dest: {path: source, relink: true}\n"
      val (previewCode, preview) = run(root, config, dryRun = true)
      val (applyCode, _)         = run(root, config)

      assertEquals(previewCode, applyCode)
      assertEquals(previewCode, 1)
      assert(!preview.contains("Would remove"), preview)
      assert(!preview.contains("Would create symlink"), preview)
      assertEquals(os.read(root / "dest"), "original")
    }
  }

  test("create without a mode preserves the permissions produced by the process umask") {
    withRoot { root =>
      Files.createDirectory((root / "control").toNIO): Unit
      val (code, _) = run(root, "- create: [generated]\n")

      assertEquals(code, 0)
      assertEquals(
        Files.getPosixFilePermissions((root / "generated").toNIO),
        Files.getPosixFilePermissions((root / "control").toNIO),
      )
    }
  }

  test("create still honors an explicit mode and defaults mode") {
    withRoot { root =>
      val (code, _) =
        run(root, "- defaults:\n    create: {mode: '0700'}\n- create:\n    private: null\n    shared: {mode: '0750'}\n")
      assertEquals(code, 0)
      assertEquals(os.perms(root / "private").value & 511, 448)
      assertEquals(os.perms(root / "shared").value & 511, 488)
    }
  }

  for defaults <-
      Vector("42", "{link: 42}", "{lnik: {force: true}}", "{link: {relnk: true}}", "{link: {type: nonsense}}")
  do
    test(s"validate rejects malformed defaults even without a later directive: $defaults") {
      withRoot { root =>
        val (code, output) = run(root, s"- defaults: $defaults\n", mode = RunMode.Validate)
        assertEquals(code, 1)
        assert(output.contains("defaults"), output)
      }
    }

  for entry <- Vector("{path: 'source[', glob: true}", "{path: 'source/*', glob: true, exclude: ['bad[']}") do
    test(s"validate rejects malformed glob syntax: $entry") {
      withRoot { root =>
        val (code, output) = run(root, s"- link:\n    dest: $entry\n", mode = RunMode.Validate)
        assertEquals(code, 1)
        assert(output.contains("glob"), output)
      }
    }

  test("glob syntax errors are returned as values") {
    assert(Glob.glob(OsFilesystem, "/tmp/source[").isLeft)
  }

  test("apply reports a malformed glob and continues with later directives") {
    withRoot { root =>
      val (code, output) = run(root, "- link:\n    dest: {path: 'source[', glob: true}\n- create: [after]\n")
      assertEquals(code, 1)
      assert(os.isDir(root / "after"), output)
      assert(output.contains("glob"), output)
    }
  }
