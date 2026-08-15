package io.worxbend.dotbot.shell

import io.worxbend.dotbot.core.ShellExit

import java.time.Duration

class ShellCommandSuite extends munit.FunSuite:
  test("commands run through a POSIX shell, not the user's login shell") {
    // Taking the interpreter from $SHELL made a config's behavior depend on who ran it: under
    // fish or tcsh the POSIX syntax that config authors write is a syntax error.
    assertEquals(OsShellRunner.shellCommand("echo ok"), Seq("/bin/sh", "-c", "echo ok"))
  }

  test("the command is passed as a single argument, so its own quoting survives") {
    val command = """test -f "a b" && echo 'yes'"""

    assertEquals(OsShellRunner.shellCommand(command), Seq("/bin/sh", "-c", command))
  }

  test("POSIX redirection in a command actually runs") {
    val exit = OsShellRunner.run(
      "command -v definitely-not-a-real-binary >/dev/null 2>&1 || true",
      ShellOptions(cwd = os.pwd.toString, timeout = Duration.ofSeconds(30)),
    )

    // This is the shape used by the example config shipped in this repository.
    assertEquals(exit, ShellExit.Completed(0))
  }

  test("a command that reads stdin does not hang when stdin is disabled") {
    val exit = OsShellRunner.run(
      "cat",
      ShellOptions(cwd = os.pwd.toString, enableStdin = false, timeout = Duration.ofSeconds(30)),
    )

    // With stdin off the pipe is closed, so `cat` sees end-of-file and exits immediately rather
    // than blocking until the timeout.
    assertEquals(exit, ShellExit.Completed(0))
  }

  test("a nonzero exit is reported with its code") {
    val exit = OsShellRunner.run("exit 3", ShellOptions(cwd = os.pwd.toString, timeout = Duration.ofSeconds(30)))

    assertEquals(exit, ShellExit.Completed(3))
    assertEquals(exit.successful, false)
    assertEquals(exit.code, 3)
  }

  test("a timeout maps to the conventional exit code 124") {
    val exit = OsShellRunner.run("sleep 5", ShellOptions(cwd = os.pwd.toString, timeout = Duration.ofMillis(100)))

    assertEquals(exit, ShellExit.TimedOut)
    assertEquals(exit.code, 124)
    assertEquals(exit.successful, false)
  }

  test("commands run in the directory they are given") {
    val root = os.temp.dir(prefix = "dotbot-scala-shell-cwd-")
    os.write(root / "marker.txt", "here")

    val exit = OsShellRunner.run(
      "test -f marker.txt",
      ShellOptions(cwd = root.toString, timeout = Duration.ofSeconds(30)),
    )

    assertEquals(exit, ShellExit.Completed(0))
  }
