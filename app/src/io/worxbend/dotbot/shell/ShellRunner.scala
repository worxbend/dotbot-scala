package io.worxbend.dotbot.shell

import ox.forkDiscard
import ox.supervised

import java.io.InputStream
import java.time.Duration
import java.util.concurrent.TimeUnit

final case class ShellOptions(
  cwd:          String,
  enableStdin:  Boolean = false,
  enableStdout: Boolean = false,
  enableStderr: Boolean = false,
  timeout:      Duration = Duration.ofMinutes(10),
)

trait ShellRunner:
  def run(command: String, options: ShellOptions): Int

object OsShellRunner extends ShellRunner:
  def run(command: String, options: ShellOptions): Int =
    val processBuilder = ProcessBuilder(shellCommand(command)*)
      .directory(java.io.File(options.cwd))

    if !options.enableStdout then processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
    if !options.enableStderr then processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)

    val process = processBuilder.start()
    if !options.enableStdin then process.getOutputStream.close()

    supervised:
      if options.enableStdout then forkDiscard(copy(process.getInputStream, System.out))
      if options.enableStderr then forkDiscard(copy(process.getErrorStream, System.err))

      val finished = process.waitFor(options.timeout.toMillis, TimeUnit.MILLISECONDS)
      if !finished then
        process.destroyForcibly()
        124
      else process.exitValue()

  private def shellCommand(command: String): Seq[String] =
    Seq(sys.env.getOrElse("SHELL", "/bin/sh"), "-c", command)

  private def copy(input: InputStream, out: java.io.PrintStream): Unit =
    input.transferTo(out)
    ()
