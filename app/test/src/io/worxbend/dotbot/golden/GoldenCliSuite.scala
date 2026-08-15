package io.worxbend.dotbot.golden

import io.worxbend.dotbot.app.DotbotApp
import io.worxbend.dotbot.cli.Cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

class GoldenCliSuite extends munit.FunSuite:
  test("version output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-cli-version-")

    val result = run(root, Array("--version"))

    assertEquals(
      result,
      GoldenCliResult(
        exitCode = 0,
        // Derived from the build rather than hardcoded: the version string is generated into
        // BuildInfo from build.mill, so a version bump must not break this golden. What is locked
        // here is the shape of the line, not the number in it.
        stdout = s"Dotbot-Scala version ${DotbotApp.version}\n",
        stderr = "",
        tree = Vector.empty,
      ),
    )
  }

  test("root config and subcommand options merge for json plan") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-cli-plan-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |- link:
        |    linked.txt: source.txt
        |""".stripMargin,
    )

    val result = run(root, Array("-c", config.toString, "plan", "--no-color", "--output", "json"))

    assertEquals(
      result,
      GoldenCliResult(
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
        stderr = "",
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("invalid plan output format is reported after planning") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-cli-plan-output-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |""".stripMargin,
    )

    val result = run(root, Array("-c", config.toString, "plan", "--no-color", "--output", "yaml"))

    assertEquals(
      result,
      GoldenCliResult(
        exitCode = 1,
        stdout = "error unsupported output format \"yaml\"\n",
        stderr = "",
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("verbose short flag cluster output is stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-cli-verbose-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |""".stripMargin,
    )

    val result = run(root, Array("-vvv", "--no-color", "--dry-run", "-c", config.toString))

    assertEquals(
      result,
      GoldenCliResult(
        exitCode = 0,
        stdout =
          s"""step  Starting dry-run with 1 task(s), 1 config file(s), base $root
             |step  Would create path $root/generated
             |info  All paths have been set up
             |info  All tasks executed successfully
             |""".stripMargin,
        stderr = "",
        tree = Vector("F install.conf.yaml"),
      ),
    )
  }

  test("only filter output and filesystem state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-cli-only-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |- shell:
        |    - "false"
        |""".stripMargin,
    )

    val result = run(root, Array("--no-color", "--only", "create", "-c", config.toString))

    assertEquals(
      result,
      GoldenCliResult(
        exitCode = 0,
        stdout =
          s"""step  Starting apply with 2 task(s), 1 config file(s), base $root
             |step  Creating path $root/generated
             |""".stripMargin,
        stderr = "",
        tree = Vector("D generated", "F install.conf.yaml"),
      ),
    )
  }

  test("except filter output and filesystem state are stable") {
    val root = os.temp.dir(prefix = "dotbot-scala-golden-cli-except-")
    val config = root / "install.conf.yaml"
    os.write(
      config,
      """- create:
        |    - generated
        |- shell:
        |    - "false"
        |""".stripMargin,
    )

    val result = run(root, Array("--no-color", "--except", "shell", "-c", config.toString))

    assertEquals(
      result,
      GoldenCliResult(
        exitCode = 0,
        stdout =
          s"""step  Starting apply with 2 task(s), 1 config file(s), base $root
             |step  Creating path $root/generated
             |""".stripMargin,
        stderr = "",
        tree = Vector("D generated", "F install.conf.yaml"),
      ),
    )
  }

  private final case class GoldenCliResult(exitCode: Int, stdout: String, stderr: String, tree: Vector[String])

  private def run(root: os.Path, args: Array[String]): GoldenCliResult =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val code = Cli.execute(args, PrintStream(stdout), PrintStream(stderr))
    GoldenCliResult(code, stdout.toString, stderr.toString, tree(root))

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
