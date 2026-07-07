package io.worxbend.dotbot.golden

import io.worxbend.dotbot.app.AppOptions
import io.worxbend.dotbot.app.AppDependencies
import io.worxbend.dotbot.app.DotbotApp
import io.worxbend.dotbot.app.OutputFormat
import io.worxbend.dotbot.app.RunMode

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GoldenAppSuite extends munit.FunSuite:
  test("plan text output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-plan-text-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |- link:
        |    linked.txt: source.txt
        |- shell:
        |    - [echo ok, Echo]
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), mode = RunMode.Plan(OutputFormat.Text), noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""Plan: 3 operation(s), 3 task(s), 1 config file(s), base $root
             |create  generated
             |link    linked.txt -> source.txt
             |shell   echo ok [Echo]
             |""".stripMargin,
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("plan json output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-plan-json-")
    val config = root / "install.toml"
    os.write(
      config,
      """tasks = [
        |  { create = ["generated"] },
        |  { link = { "linked.txt" = "source.txt" } },
        |]
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), mode = RunMode.Plan(OutputFormat.Json), noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""{
             |  "task_count": 2,
             |  "config_file_count": 1,
             |  "operation_count": 2,
             |  "base": "$root",
             |  "operations": [
             |    {
             |      "directive": "create",
             |      "target": "generated",
             |      "detail": ""
             |    },
             |    {
             |      "directive": "link",
             |      "target": "linked.txt",
             |      "detail": "source.txt"
             |    }
             |  ]
             |}
             |""".stripMargin,
        tree = Vector("F install.toml"),
      ),
    )
  }

  test("dry run create output and filesystem state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-dry-run-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), dryRun = true, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting dry-run with 1 task(s), 1 config file(s), base $root
             |step  Would create path $root/generated
             |""".stripMargin,
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("apply create output and filesystem state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-apply-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting apply with 1 task(s), 1 config file(s), base $root
             |step  Creating path $root/generated
             |""".stripMargin,
        tree = Vector("D generated", "F install.conf.yaml"),
      ),
    )
  }

  test("validate happy output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-validate-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), mode = RunMode.Validate, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Configuration is valid: 1 task(s), 1 config file(s), 1 planned operation(s), base $root
             |""".stripMargin,
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("dry run clean broken link output and filesystem state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-clean-dry-run-")
    val config = root / "install.conf.yaml"
    os.makeDir(root / "cleanable")
    Files.createSymbolicLink(Paths.get((root / "cleanable" / "stale").toString), Paths.get("../missing.txt"))
    os.write(
      config,
      """- clean:
        |    - cleanable
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), dryRun = true, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting dry-run with 1 task(s), 1 config file(s), base $root
             |step  Would remove invalid link $root/cleanable/stale -> $root/missing.txt
             |""".stripMargin,
        tree = Vector(
          "D cleanable",
          "F install.conf.yaml",
          "L cleanable/stale -> ../missing.txt",
        ),
      ),
    )
  }

  test("apply clean removes in-base broken links") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-clean-apply-")
    val config = root / "install.conf.yaml"
    os.makeDir(root / "cleanable")
    Files.createSymbolicLink(Paths.get((root / "cleanable" / "stale").toString), Paths.get("../missing.txt"))
    os.write(
      config,
      """- clean:
        |    - cleanable
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting apply with 1 task(s), 1 config file(s), base $root
             |step  Removing invalid link $root/cleanable/stale -> $root/missing.txt
             |""".stripMargin,
        tree = Vector("D cleanable", "F install.conf.yaml"),
      ),
    )
  }

  test("backup link output and filesystem state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-backup-link-")
    val config = root / "install.conf.yaml"
    os.write(root / "source.txt", "source\n")
    os.write(root / "existing.txt", "old\n")
    os.write(
      config,
      """- link:
        |    existing.txt:
        |      path: source.txt
        |      backup: true
        |      create: false
        |""".stripMargin,
    )

    val fixedClock = Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC)
    val result =
      run(root, AppOptions(configFiles = Vector(config.toString), noColor = true), AppDependencies(clock = fixedClock))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting apply with 1 task(s), 1 config file(s), base $root
             |step  Backed up file existing.txt to existing.txt.dotbot-backup.20240102-030405
             |step  Creating symlink existing.txt -> $root/source.txt
             |""".stripMargin,
        tree = Vector(
          "F existing.txt.dotbot-backup.20240102-030405",
          "F install.conf.yaml",
          "F source.txt",
          s"L existing.txt -> $root/source.txt",
        ),
      ),
    )
  }

  test("relink output and symlink state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-relink-")
    val config = root / "install.conf.yaml"
    os.write(root / "old.txt", "old\n")
    os.write(root / "source.txt", "source\n")
    Files.createSymbolicLink(Paths.get((root / "linked.txt").toString), Paths.get((root / "old.txt").toString))
    os.write(
      config,
      """- link:
        |    linked.txt:
        |      path: source.txt
        |      relink: true
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting apply with 1 task(s), 1 config file(s), base $root
             |step  Removing linked.txt
             |step  Creating symlink linked.txt -> $root/source.txt
             |""".stripMargin,
        tree = Vector(
          "F install.conf.yaml",
          "F old.txt",
          "F source.txt",
          s"L linked.txt -> $root/source.txt",
        ),
      ),
    )
  }

  test("dry run hardlink output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-hardlink-")
    val config = root / "install.conf.yaml"
    os.write(root / "source.txt", "source\n")
    os.write(
      config,
      """- link:
        |    linked.txt:
        |      path: source.txt
        |      type: hardlink
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), dryRun = true, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting dry-run with 1 task(s), 1 config file(s), base $root
             |step  Would create hardlink linked.txt -> $root/source.txt
             |""".stripMargin,
        tree = Vector("F install.conf.yaml", "F source.txt"),
      ),
    )
  }

  test("globbed link output and filesystem state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-link-glob-")
    val config = root / "install.conf.yaml"
    os.makeDir.all(root / "source" / "nested")
    os.write(root / "source" / "nested" / "keep.txt", "keep\n")
    os.write(root / "source" / "nested" / "skip.txt", "skip\n")
    os.write(
      config,
      """- link:
        |    linked:
        |      path: source/**/*.txt
        |      glob: true
        |      create: true
        |      prefix: p-
        |      exclude:
        |        - source/nested/skip.txt
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting apply with 1 task(s), 1 config file(s), base $root
             |step  Creating directory $root/linked/p-source/nested
             |step  Creating symlink linked/p-source/nested/keep.txt -> $root/source/nested/keep.txt
             |""".stripMargin,
        tree = Vector(
          "D linked",
          "D linked/p-source",
          "D linked/p-source/nested",
          "D source",
          "D source/nested",
          "F install.conf.yaml",
          "F source/nested/keep.txt",
          "F source/nested/skip.txt",
          s"L linked/p-source/nested/keep.txt -> $root/source/nested/keep.txt",
        ),
      ),
    )
  }

  test("dry run shell forms output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-shell-dry-run-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- shell:
        |    - echo plain
        |    - [echo pair, Pair]
        |    - command: echo mapped
        |      description: Mapped
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), dryRun = true, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 0,
        stdout =
          s"""step  Starting dry-run with 1 task(s), 1 config file(s), base $root
             |step  Would run command echo plain
             |step  Would run command Pair [echo pair]
             |step  Would run command Mapped [echo mapped]
             |""".stripMargin,
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("failing shell command output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-shell-fail-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- shell:
        |    - "false"
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 1,
        stdout =
          s"""step  Starting apply with 1 task(s), 1 config file(s), base $root
             |step  false
             |warn  Command [false] failed
             |error Some commands were not successfully executed
             |error Some tasks were not executed successfully
             |""".stripMargin,
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("invalid link type validation output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-link-type-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- link:
        |    linked.txt:
        |      path: source.txt
        |      type: socket
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), mode = RunMode.Validate, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 1,
        stdout = "error validating action link: link type is not recognized: socket\n",
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("unknown directive validation output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-unknown-directive-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- unknown:
        |    value: true
        |""".stripMargin,
    )

    val result = run(root, AppOptions(configFiles = Vector(config.toString), mode = RunMode.Validate, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 1,
        stdout = "error action unknown not handled\n",
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("missing config file list output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-no-config-")

    val result = run(root, AppOptions(noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 1,
        stdout = "error No configuration file specified\n",
        tree = Vector.empty,
      ),
    )
  }

  test("force color and no color conflict output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-color-conflict-")

    val result = run(root, AppOptions(forceColor = true, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 1,
        stdout = "error `--force-color` and `--no-color` cannot both be provided\n",
        tree = Vector.empty,
      ),
    )
  }

  test("json5 configs are rejected") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-json5-")
    val config = root / "install.json5"
    os.write(config, "[]\n")

    val result = run(root, AppOptions(configFiles = Vector(config.toString), mode = RunMode.Validate, noColor = true))

    assertEquals(
      result,
      GoldenResult(
        exitCode = 1,
        stdout = "error unsupported config file format .json5\n",
        tree = Vector("F install.json5"),
      ),
    )
  }

  private final case class GoldenResult(exitCode: Int, stdout: String, tree: Vector[String])

  private def run(root: os.Path, options: AppOptions, deps: AppDependencies = AppDependencies()): GoldenResult =
    val output = ByteArrayOutputStream()
    val code = DotbotApp.run(options, PrintStream(output), deps)
    GoldenResult(code, output.toString, tree(root))

  private def tree(root: os.Path): Vector[String] =
    os.walk(root)
      .filter(_ != root)
      .map { path =>
        val rel = path.relativeTo(root).toString
        val nioPath = Paths.get(path.toString)
        if Files.isSymbolicLink(nioPath) then s"L $rel -> ${Files.readSymbolicLink(nioPath)}"
        else if os.isDir(path) then s"D $rel"
        else s"F $rel"
      }
      .sorted
      .toVector
