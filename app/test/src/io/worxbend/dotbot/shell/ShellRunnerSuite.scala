package io.worxbend.dotbot.shell

import io.worxbend.dotbot.core.ShellExit
import io.worxbend.dotbot.core.ShellOptions

import java.time.Duration

class ShellRunnerSuite extends munit.FunSuite:
  test("OsShellRunner reports completed exit codes") {
    val root = os.temp.dir(prefix = "dotbot-scala-shell-runner-completed-")

    val shellExit = OsShellRunner.run("exit 7", ShellOptions(cwd = root.toString))

    assertEquals(shellExit, ShellExit.Completed(7))
    assert(!shellExit.successful)
  }

  test("OsShellRunner reports successful commands") {
    val root = os.temp.dir(prefix = "dotbot-scala-shell-runner-success-")

    val shellExit = OsShellRunner.run("true", ShellOptions(cwd = root.toString))

    assertEquals(shellExit, ShellExit.Completed(0))
    assert(shellExit.successful)
  }

  test("OsShellRunner maps a timeout to ShellExit.TimedOut") {
    val root = os.temp.dir(prefix = "dotbot-scala-shell-runner-timeout-")

    val shellExit = OsShellRunner.run("sleep 1", ShellOptions(cwd = root.toString, timeout = Duration.ofMillis(10)))

    assertEquals(shellExit, ShellExit.TimedOut)
    assert(!shellExit.successful)
  }
