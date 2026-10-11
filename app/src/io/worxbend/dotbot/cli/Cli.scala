package io.worxbend.dotbot.cli

import io.worxbend.dotbot.app.DotbotApp
import io.worxbend.dotbot.app.OutputFormat
import io.worxbend.dotbot.core.Directive

import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.ISetter
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.ParseResult

import java.io.PrintStream
import java.io.PrintWriter
import java.time.Duration

/**
 * Top-level CLI entrypoint and command wiring.
 */
object Cli:
  /**
   * Parse args, build command mode, and execute dotbot.
   *
   * @param args command line arguments.
   * @param stdout command output stream.
   * @param stderr command error stream (also used for help output).
   */
  def execute(args: Array[String], stdout: PrintStream = System.out, stderr: PrintStream = System.err): Int =
    val rootState     = CliStateBuilder()
    val validateState = CliStateBuilder()
    val planState     = CliStateBuilder()

    val root = CommandSpec
      .create()
      .name("dotbot-scala")
      .version(s"Dotbot-Scala version ${DotbotApp.version}")
    root.usageMessage().description("A Scala 3 port of Dotbot for bootstrapping dotfiles")
    addHelpOptions(root)
    addCommonOptions(root, rootState)
    addApplyOptions(root, rootState)

    val validate = CommandSpec
      .create()
      .name("validate")
    validate.usageMessage().description("Validate configuration without applying changes")
    addHelpOptions(validate)
    addCommonOptions(validate, validateState)

    val plan = CommandSpec
      .create()
      .name("plan")
    plan.usageMessage().description("Print planned operations without applying changes")
    addHelpOptions(plan)
    addCommonOptions(plan, planState)
    plan.addOption(
      stringOption(
        Array("--output"),
        "output format: text or json",
        value => planState.setPlanOutput(OutputFormat.fromString(value)),
      ),
    )

    root.addSubcommand("validate", validate)
    root.addSubcommand("plan", plan)

    val command = CommandLine(root)
    command.setOverwrittenOptionsAllowed(true)
    command.setOut(PrintWriter(stdout, true))
    command.setErr(PrintWriter(stderr, true))
    command.setExecutionStrategy { parseResult =>
      if parseResult.isUsageHelpRequested || parseResult.isVersionHelpRequested then
        CommandLine.executeHelpRequest(parseResult)
      else cliCommand(parseResult, rootState.state, validateState.state, planState.state).execute(stdout, stderr)
    }
    command.execute(args*)

  private def cliCommand(
      parseResult: ParseResult,
      rootState: CliState,
      validateState: CliState,
      planState: CliState,
  ): CliCommand =
    if parseResult.hasSubcommand then
      parseResult.subcommand().commandSpec().name() match
        case "validate" => CliCommand.Validate(validateState.mergedWith(rootState))
        case "plan"     => CliCommand.Plan(planState.mergedWith(rootState))
        case _          => CliCommand.Unknown
    else CliCommand.Apply(rootState)

  private def addCommonOptions(spec: CommandSpec, state: CliStateBuilder): Unit =
    spec.addOption(booleanOption(Array("-q", "--quiet"), "suppress most output", state.enableQuiet)): Unit
    spec.addOption(verboseOption(state)): Unit
    spec.addOption(
      stringOption(
        Array("-d", "--base-directory"),
        "execute commands from within BASE_DIR",
        state.setBaseDirectory,
        "BASE_DIR",
      ),
    ): Unit
    spec.addOption(
      stringOption(
        Array("-c", "--config-file"),
        "run commands given in CONFIG_FILE",
        state.addConfigFile,
        "CONFIG_FILE",
      ),
    ): Unit
    spec.addOption(
      stringOption(
        Array("--only"),
        "only run specified directives",
        value => applyDirectiveList(state, value, state.addOnly),
      ),
    ): Unit
    spec.addOption(
      stringOption(
        Array("--except"),
        "skip specified directives",
        value => applyDirectiveList(state, value, state.addSkip),
      ),
    ): Unit
    spec.addOption(booleanOption(Array("--force-color"), "force color output", state.enableForceColor)): Unit
    spec.addOption(booleanOption(Array("--no-color"), "disable color output", state.enableNoColor)): Unit
    spec.addOption(booleanOption(Array("--emoji"), "enable emoji/symbol output", state.enableForceEmoji)): Unit
    spec.addOption(booleanOption(Array("--no-emoji"), "disable emoji/symbol output", state.enableNoEmoji)): Unit

  private def addHelpOptions(spec: CommandSpec): Unit =
    spec.addOption(
      OptionSpec
        .builder(Array("-h", "--help"))
        .description("Show this help message and exit.")
        .usageHelp(true)
        .build(),
    ): Unit
    spec.addOption(
      OptionSpec
        .builder(Array("-V", "--version"))
        .description("Print version information and exit.")
        .versionHelp(true)
        .build(),
    ): Unit

  private def addApplyOptions(spec: CommandSpec, state: CliStateBuilder): Unit =
    spec.addOption(
      booleanOption(Array("-n", "--dry-run"), "print what would be done, without doing it", state.enableDryRun),
    ): Unit
    spec.addOption(
      booleanOption(Array("-x", "--exit-on-failure"), "exit after first failed directive", state.enableExitOnFailure),
    ): Unit
    spec.addOption(
      stringOption(
        Array("--shell-timeout"),
        "how long a shell command or link condition may run, in seconds (default 600)",
        value => applyShellTimeout(state, value),
        "SECONDS",
      ),
    ): Unit

  private def verboseOption(state: CliStateBuilder): OptionSpec =
    OptionSpec
      .builder(Array("-v", "--verbose"))
      .description("-v: show informational messages; -vv: also stream shell command output")
      .`type`(classOf[java.lang.Boolean])
      .arity("0")
      .setter(setter(value => if value == java.lang.Boolean.TRUE then state.incrementVerbose()))
      .build()

  private def booleanOption(names: Array[String], description: String, update: () => Unit): OptionSpec =
    OptionSpec
      .builder(names)
      .description(description)
      .`type`(classOf[java.lang.Boolean])
      .arity("0")
      .setter(setter(value => if value == java.lang.Boolean.TRUE then update()))
      .build()

  private def stringOption(
      names: Array[String],
      description: String,
      update: String => Unit,
      paramLabel: String = "",
  ): OptionSpec =
    val builder = OptionSpec
      .builder(names)
      .description(description)
      .`type`(classOf[String])
      .setter(setter(value => Option(value).foreach(item => update(String.valueOf(item)))))
    if paramLabel.nonEmpty then builder.paramLabel(paramLabel): Unit
    builder.build()

  private def setter(update: Any => Unit): ISetter =
    new ISetter:
      override def set[T](value: T): T =
        update(value)
        value

  /**
   * Parse a comma-separated `--only` / `--except` list into directives.
   *
   * Unknown names are rejected rather than turned into `Directive.Unknown`. A typo used to be
   * accepted silently and then matched no directive at all, so `--only lnik` skipped every action,
   * printed "All tasks executed successfully" and exited 0 — telling the user their dotfiles were
   * installed when nothing had run.
   */
  private[cli] def splitDirectiveList(value: String): Either[Vector[String], Vector[Directive]] =
    val names   = value.split(",").iterator.map(_.trim).filter(_.nonEmpty).toVector
    val unknown = names.filter(Directive.fromString(_).isEmpty)
    if unknown.nonEmpty then Left(unknown)
    else Right(names.flatMap(Directive.fromString))

  /**
   * Parse a `--shell-timeout` value, given in whole seconds.
   *
   * A value that is not a positive number is rejected rather than quietly falling back to the
   * default: a typo that silently restored the ten-minute timeout would look exactly like the flag
   * working, and would only show up as a hang.
   */
  private[cli] def parseShellTimeout(value: String): Option[Duration] =
    value.toIntOption.filter(_ > 0).map(seconds => Duration.ofSeconds(seconds.toLong))

  private def applyShellTimeout(state: CliStateBuilder, value: String): Unit =
    parseShellTimeout(value) match
      case Some(timeout) => state.setShellTimeout(timeout)
      case None          => state.addInvalidShellTimeout(value)

  private def applyDirectiveList(
      state: CliStateBuilder,
      value: String,
      accept: Vector[Directive] => Unit,
  ): Unit =
    splitDirectiveList(value) match
      case Right(directives) => accept(directives)
      case Left(unknown)     => state.addUnknownDirectives(unknown)
