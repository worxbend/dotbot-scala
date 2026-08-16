package io.worxbend.dotbot.shell

import io.worxbend.dotbot.core.ShellExit as CoreShellExit
import io.worxbend.dotbot.core.ShellOptions as CoreShellOptions
import io.worxbend.dotbot.core.ShellRunner as CoreShellRunner
import ox.forkDiscard
import ox.supervised

import java.io.InputStream
import java.util.concurrent.TimeUnit

type ShellOptions = CoreShellOptions
val ShellOptions: CoreShellOptions.type = CoreShellOptions

type ShellExit = CoreShellExit
val ShellExit: CoreShellExit.type = CoreShellExit

type ShellRunner = CoreShellRunner

object OsShellRunner extends ShellRunner:
  def run(command: String, options: ShellOptions): ShellExit =
    val processBuilder = ProcessBuilder(shellCommand(command)*)
      .directory(java.io.File(options.cwd))

    if !options.enableStdout then processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD): Unit
    if !options.enableStderr then processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD): Unit

    // With stdin enabled the child must inherit the real terminal. Leaving it as the default pipe
    // meant an interactive command (`read -p "Continue?"`) blocked on a stream nobody ever wrote
    // to, showed the user no prompt, and was killed as a timeout ten minutes later.
    if options.enableStdin then processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT): Unit

    val process = processBuilder.start()
    if !options.enableStdin then process.getOutputStream.close()

    supervised:
      if options.enableStdout then forkDiscard(copy(process.getInputStream, System.out))
      if options.enableStderr then forkDiscard(copy(process.getErrorStream, System.err))

      val finished = process.waitFor(options.timeout.toMillis, TimeUnit.MILLISECONDS)
      if !finished then
        terminate(process)
        ShellExit.TimedOut
      else ShellExit.Completed(process.exitValue())

  /**
   * Kill a timed-out command and everything it started.
   *
   * `destroyForcibly` only signals the shell itself and returns before it has died. A command
   * such as `foo | bar &` leaves its children running, still holding the pipes this scope is
   * about to wait on, so both the orphans and the wait outlive the timeout. Descendants are
   * therefore killed first, and the wait afterwards is what makes the timeout mean the command
   * has actually stopped.
   */
  private def terminate(process: Process): Unit =
    process.descendants().forEach(_.destroyForcibly(): Unit)
    process.destroyForcibly()
    process.waitFor(): Unit

  /**
   * Build the argv used to run a `shell` directive.
   *
   * Commands are run through `/bin/sh` rather than the user's `$SHELL`. Config authors write
   * POSIX shell — the example config in this repository uses `>/dev/null 2>&1` and `&&`/`||` —
   * and under a non-POSIX login shell such as fish or tcsh the same directive is a syntax error.
   * That made a config's behavior depend on who ran it, and it let any process that can set
   * `$SHELL` choose the interpreter for dotbot's commands. Upstream Dotbot uses `/bin/sh` too.
   */
  private[shell] def shellCommand(command: String): Seq[String] =
    Seq(PosixShell, "-c", command)

  private val PosixShell: String = "/bin/sh"

  private def copy(input: InputStream, out: java.io.PrintStream): Unit =
    input.transferTo(out)
    ()
