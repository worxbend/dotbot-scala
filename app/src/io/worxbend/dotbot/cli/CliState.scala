package io.worxbend.dotbot.cli

import io.worxbend.dotbot.app.AppOptions
import io.worxbend.dotbot.app.DotbotApp
import io.worxbend.dotbot.app.OutputFormat
import io.worxbend.dotbot.app.OutputFormatArgument
import io.worxbend.dotbot.app.RunMode
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.logging.TerminalText

import java.io.PrintStream
import java.time.Duration

/**
 * Immutable parser state for one command context (root/apply, validate, or plan).
 */
final private[cli] case class CliState(
    quiet: Boolean = false,
    verbose: Int = 0,
    baseDirectory: String = "",
    configFiles: List[String] = Nil,
    only: Vector[Directive] = Vector.empty,
    skip: Vector[Directive] = Vector.empty,
    forceColor: Boolean = false,
    noColor: Boolean = false,
    forceEmoji: Boolean = false,
    noEmoji: Boolean = false,
    dryRun: Boolean = false,
    exitOnFailure: Boolean = false,
    shellTimeout: Duration = CliState.DefaultShellTimeout,
    planOutput: OutputFormatArgument = OutputFormatArgument.Valid(OutputFormat.Text),
    unknownDirectives: Vector[String] = Vector.empty,
    invalidShellTimeouts: Vector[String] = Vector.empty,
):
  /**
   * Merge a nested subcommand state with values inherited from the parent command.
   */
  def mergedWith(parent: CliState): CliState =
    copy(
      quiet = parent.quiet || quiet,
      verbose = parent.verbose + verbose,
      baseDirectory = if baseDirectory.nonEmpty then baseDirectory else parent.baseDirectory,
      configFiles = configFiles ++ parent.configFiles,
      only = only ++ parent.only,
      skip = skip ++ parent.skip,
      forceColor = parent.forceColor || forceColor,
      noColor = parent.noColor || noColor,
      forceEmoji = parent.forceEmoji || forceEmoji,
      noEmoji = parent.noEmoji || noEmoji,
      dryRun = parent.dryRun || dryRun,
      exitOnFailure = parent.exitOnFailure || exitOnFailure,
      shellTimeout = if shellTimeout != CliState.DefaultShellTimeout then shellTimeout else parent.shellTimeout,
      unknownDirectives = parent.unknownDirectives ++ unknownDirectives,
      invalidShellTimeouts = parent.invalidShellTimeouts ++ invalidShellTimeouts,
      planOutput =
        if planOutput != OutputFormatArgument.Valid(OutputFormat.Text) then planOutput
        else parent.planOutput,
    )

  def appOptions(mode: RunMode = RunMode.Apply): AppOptions =
    AppOptions(
      quiet = quiet,
      verbose = verbose,
      baseDirectory = baseDirectory,
      configFiles = configFiles.reverse.toVector,
      only = only.toSet,
      skip = skip.toSet,
      dryRun = dryRun,
      forceColor = forceColor,
      noColor = noColor,
      forceEmoji = forceEmoji,
      noEmoji = noEmoji,
      exitOnFailure = exitOnFailure,
      shellTimeout = shellTimeout,
      mode = mode,
    )

  def planMode: RunMode =
    planOutput.toRunMode

private[cli] object CliState:
  val DefaultShellTimeout: Duration = AppOptions().shellTimeout

/**
 * Builder scoped to one Picocli execution; it owns parser mutation.
 */
final private[cli] class CliStateBuilder:
  // Picocli's programmatic setters are effectful; keep that mutation at this boundary.
  private var current = CliState()

  def state: CliState =
    current

  def enableQuiet(): Unit =
    update(_.copy(quiet = true))

  def incrementVerbose(): Unit =
    update(state => state.copy(verbose = state.verbose + 1))

  def setBaseDirectory(value: String): Unit =
    update(_.copy(baseDirectory = value))

  def addConfigFile(value: String): Unit =
    update(state => state.copy(configFiles = value :: state.configFiles))

  def addOnly(values: Vector[Directive]): Unit =
    update(state => state.copy(only = state.only ++ values))

  def addSkip(values: Vector[Directive]): Unit =
    update(state => state.copy(skip = state.skip ++ values))

  def addUnknownDirectives(names: Vector[String]): Unit =
    update(state => state.copy(unknownDirectives = state.unknownDirectives ++ names))

  def enableForceColor(): Unit =
    update(_.copy(forceColor = true))

  def enableNoColor(): Unit =
    update(_.copy(noColor = true))

  def enableForceEmoji(): Unit =
    update(_.copy(forceEmoji = true))

  def enableNoEmoji(): Unit =
    update(_.copy(noEmoji = true))

  def enableDryRun(): Unit =
    update(_.copy(dryRun = true))

  def enableExitOnFailure(): Unit =
    update(_.copy(exitOnFailure = true))

  def setShellTimeout(value: Duration): Unit =
    update(_.copy(shellTimeout = value))

  def addInvalidShellTimeout(value: String): Unit =
    update(state => state.copy(invalidShellTimeouts = state.invalidShellTimeouts :+ value))

  def setPlanOutput(value: OutputFormatArgument): Unit =
    update(_.copy(planOutput = value))

  private def update(f: CliState => CliState): Unit =
    current = f(current)

private[cli] enum CliCommand:
  case Apply(state: CliState)
  case Validate(state: CliState)
  case Plan(state: CliState)
  case Unknown

  def execute(stdout: PrintStream, stderr: PrintStream): Int =
    this match
      case CliCommand.Apply(state)    => run(state, state.appOptions(), stdout, stderr)
      case CliCommand.Validate(state) => run(state, state.appOptions(RunMode.Validate), stdout, stderr)
      case CliCommand.Plan(state)     => run(state, state.appOptions(state.planMode), stdout, stderr)
      case CliCommand.Unknown         => 1

  private def run(state: CliState, options: AppOptions, stdout: PrintStream, stderr: PrintStream): Int =
    if state.unknownDirectives.nonEmpty then
      stderr.println(s"error unknown directive(s): ${TerminalText.escape(state.unknownDirectives.mkString(", "))}")
      1
    else if state.invalidShellTimeouts.nonEmpty then
      stderr.println(
        s"error invalid --shell-timeout value(s): ${TerminalText.escape(state.invalidShellTimeouts.mkString(", "))}",
      )
      1
    else DotbotApp.run(options, stdout, stderr = stderr)
