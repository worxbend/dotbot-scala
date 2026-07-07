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

    val process = processBuilder.start()
    if !options.enableStdin then process.getOutputStream.close()

    supervised:
      if options.enableStdout then forkDiscard(copy(process.getInputStream, System.out))
      if options.enableStderr then forkDiscard(copy(process.getErrorStream, System.err))

      val finished = process.waitFor(options.timeout.toMillis, TimeUnit.MILLISECONDS)
      if !finished then
        process.destroyForcibly()
        ShellExit.TimedOut
      else ShellExit.Completed(process.exitValue())

  private def shellCommand(command: String): Seq[String] =
    Seq(sys.env.getOrElse("SHELL", "/bin/sh"), "-c", command)

  private def copy(input: InputStream, out: java.io.PrintStream): Unit =
    input.transferTo(out)
    ()
