package io.worxbend.dotbot.cli

import io.worxbend.dotbot.app.AppOptions
import io.worxbend.dotbot.app.DotbotApp
import io.worxbend.dotbot.app.OutputFormat
import io.worxbend.dotbot.app.OutputFormatArgument
import io.worxbend.dotbot.app.RunMode
import io.worxbend.dotbot.core.Directive

import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.ISetter
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.ParseResult

import java.io.PrintStream
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

/**
 * Mutable parser state for one command context (root/apply, validate, or plan).
 */
private final case class CliState(
  quiet:         Boolean = false,
  verbose:       Int = 0,
  baseDirectory: String = "",
  configFiles:   List[String] = Nil,
  only:          Vector[Directive] = Vector.empty,
  skip:          Vector[Directive] = Vector.empty,
  forceColor:    Boolean = false,
  noColor:       Boolean = false,
  forceEmoji:    Boolean = false,
  noEmoji:       Boolean = false,
  dryRun:        Boolean = false,
  exitOnFailure: Boolean = false,
  planOutput:    OutputFormatArgument = OutputFormatArgument.Valid(OutputFormat.Text),
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
      mode = mode,
    )

  def planMode: RunMode =
    planOutput.toRunMode

/**
 * Thread-safe builder that owns Picocli mutation side effects.
 */
private final class CliStateBuilder:
  // Picocli's programmatic setters are effectful; keep that mutation at this boundary.
  private val current = AtomicReference(CliState())

  def state: CliState =
    current.get()

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

  def setPlanOutput(value: OutputFormatArgument): Unit =
    update(_.copy(planOutput = value))

  private def update(f: CliState => CliState): Unit =
    val updater = new UnaryOperator[CliState]:
      def apply(state: CliState): CliState =
        f(state)
    current.updateAndGet(updater) match
      case _ => ()

private enum CliCommand:
  case Apply(state: CliState)
  case Validate(state: CliState)
  case Plan(state: CliState)
  case Unknown

  def execute(stdout: PrintStream): Int =
    this match
      case CliCommand.Apply(state)    => DotbotApp.run(state.appOptions(), stdout)
      case CliCommand.Validate(state) => DotbotApp.run(state.appOptions(RunMode.Validate), stdout)
      case CliCommand.Plan(state)     => DotbotApp.run(state.appOptions(state.planMode), stdout)
      case CliCommand.Unknown         => 1

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
    val rootState = CliStateBuilder()
    val validateState = CliStateBuilder()
    val planState = CliStateBuilder()

    val root = CommandSpec.create()
      .name("dotbot-scala")
      .version(s"Dotbot-Scala version ${DotbotApp.version}")
    root.usageMessage().description("A Scala 3 port of Dotbot for bootstrapping dotfiles")
    addHelpOptions(root)
    addCommonOptions(root, rootState)
    addApplyOptions(root, rootState)

    val validate = CommandSpec.create()
      .name("validate")
    validate.usageMessage().description("Validate configuration without applying changes")
    addHelpOptions(validate)
    addCommonOptions(validate, validateState)

    val plan = CommandSpec.create()
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
      else cliCommand(parseResult, rootState.state, validateState.state, planState.state).execute(stdout)
    }
    command.execute(args*)

  private def cliCommand(
    parseResult:   ParseResult,
    rootState:     CliState,
    validateState: CliState,
    planState:     CliState,
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
        value => state.addOnly(splitDirectiveList(value)),
      ),
    ): Unit
    spec.addOption(
      stringOption(
        Array("--except"),
        "skip specified directives",
        value => state.addSkip(splitDirectiveList(value)),
      ),
    ): Unit
    spec.addOption(booleanOption(Array("--force-color"), "force color output", state.enableForceColor)): Unit
    spec.addOption(booleanOption(Array("--no-color"), "disable color output", state.enableNoColor)): Unit
    spec.addOption(booleanOption(Array("--emoji"), "enable emoji/symbol output", state.enableForceEmoji)): Unit
    spec.addOption(booleanOption(Array("--no-emoji"), "disable emoji/symbol output", state.enableNoEmoji)): Unit

  private def addHelpOptions(spec: CommandSpec): Unit =
    spec.addOption(
      OptionSpec.builder(Array("-h", "--help"))
        .description("Show this help message and exit.")
        .usageHelp(true)
        .build(),
    ): Unit
    spec.addOption(
      OptionSpec.builder(Array("-V", "--version"))
        .description("Print version information and exit.")
        .versionHelp(true)
        .build(),
    ): Unit

  private def addApplyOptions(spec: CommandSpec, state: CliStateBuilder): Unit =
    spec.addOption(booleanOption(Array("-n", "--dry-run"), "print what would be done, without doing it", state.enableDryRun)): Unit
    spec.addOption(booleanOption(Array("-x", "--exit-on-failure"), "exit after first failed directive", state.enableExitOnFailure)): Unit

  private def verboseOption(state: CliStateBuilder): OptionSpec =
    OptionSpec.builder(Array("-v", "--verbose"))
      .description("-v: show informational messages; -vv: also stream shell command output")
      .`type`(classOf[java.lang.Boolean])
      .arity("0")
      .setter(setter(value => if value == java.lang.Boolean.TRUE then state.incrementVerbose()))
      .build()

  private def booleanOption(names: Array[String], description: String, update: () => Unit): OptionSpec =
    OptionSpec.builder(names)
      .description(description)
      .`type`(classOf[java.lang.Boolean])
      .arity("0")
      .setter(setter(value => if value == java.lang.Boolean.TRUE then update()))
      .build()

  private def stringOption(
    names:       Array[String],
    description: String,
    update:      String => Unit,
    paramLabel:  String = "",
  ): OptionSpec =
    val builder = OptionSpec.builder(names)
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

  private def splitDirectiveList(value: String): Vector[Directive] =
    value.split(",").iterator.map(_.trim).filter(_.nonEmpty).map(Directive.parse).toVector
