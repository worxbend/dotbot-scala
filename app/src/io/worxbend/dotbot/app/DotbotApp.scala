package io.worxbend.dotbot.app

import io.worxbend.dotbot.config.ConfigReader
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.Filesystem
import io.worxbend.dotbot.core.PathUtil
import io.worxbend.dotbot.core.ShellRunner
import io.worxbend.dotbot.core.Task
import io.worxbend.dotbot.fs.OsFilesystem
import io.worxbend.dotbot.logging.ColorSupport
import io.worxbend.dotbot.logging.Level
import io.worxbend.dotbot.logging.SymbolSupport
import io.worxbend.dotbot.logging.Logger
import io.worxbend.dotbot.shell.OsShellRunner

import java.io.PrintStream
import java.time.Clock

/**
 * Runtime configuration passed to `DotbotApp`.
 *
 * @param quiet show only warnings and errors.
 * @param verbose 0 = action-level logs, 1 = include informational logs, 2+ = include debug logs.
 * @param baseDirectory base directory override; defaults to the config file parent.
 * @param configFiles input configuration files.
 * @param only run only the listed directives.
 * @param skip skip listed directives.
 * @param dryRun skip side effects and log intended actions.
 * @param forceColor force ANSI colors even if detection says no.
 * @param noColor disable ANSI colors.
 * @param forceEmoji force emoji/symbol output.
 * @param noEmoji disable emoji/symbol output.
 * @param exitOnFailure abort after first failed directive.
 * @param mode execution mode: apply, validate, or plan.
 */
final case class AppOptions(
  quiet:         Boolean = false,
  verbose:       Int = 0,
  baseDirectory: String = "",
  configFiles:   Vector[String] = Vector.empty,
  only:          Set[Directive] = Set.empty,
  skip:          Set[Directive] = Set.empty,
  dryRun:        Boolean = false,
  forceColor:    Boolean = false,
  noColor:       Boolean = false,
  forceEmoji:    Boolean = false,
  noEmoji:       Boolean = false,
  exitOnFailure: Boolean = false,
  mode:          RunMode = RunMode.Apply,
)

/**
 * Overridable runtime dependencies for composition/testing.
 */
final case class AppDependencies(
  reader: ConfigReader = ConfigReader(),
  fs:     Filesystem = OsFilesystem,
  shell:  ShellRunner = OsShellRunner,
  clock:  Clock = Clock.systemDefaultZone(),
)

/**
 * Main application entrypoint used by the CLI and test harness.
 */
object DotbotApp:
  val version: String = sys.env.getOrElse("DOTBOT_SCALA_VERSION", BuildInfo.version)

  /**
   * Run the application with the provided options and dependencies.
   *
   * @return process exit code.
   */
  def run(options: AppOptions, stdout: PrintStream = System.out, deps: AppDependencies = AppDependencies()): Int =
    val logger = loggerFor(stdout, options)
    load(options, deps, logger) match
      case Left(exitCode) => exitCode
      case Right(loaded)  => execute(options, stdout, deps, logger, loaded)

  private def load(options: AppOptions, deps: AppDependencies, logger: Logger): Either[Int, LoadedConfig] =
    for
      _ <- validateStartupOptions(options, logger)
      tasks <- readTasks(options, deps, logger)
      _ = warnIfNoTasks(options, tasks, logger)
      base <- resolveBaseDirectory(options, deps, logger)
    yield LoadedConfig(tasks, base)

  private def validateStartupOptions(options: AppOptions, logger: Logger): Either[Int, Unit] =
    if options.forceColor && options.noColor then
      logger.error("`--force-color` and `--no-color` cannot both be provided")
      Left(1)
    else if options.forceEmoji && options.noEmoji then
      logger.error("`--emoji` and `--no-emoji` cannot both be provided")
      Left(1)
    else if options.configFiles.isEmpty then
      logger.error("No configuration file specified")
      Left(1)
    else Right(())

  private def readTasks(options: AppOptions, deps: AppDependencies, logger: Logger): Either[Int, Vector[Task]] =
    deps.reader.read(options.configFiles) match
      case Left(error) =>
        logger.error(error.render)
        Left(1)
      case Right(tasks) => Right(tasks)

  private def warnIfNoTasks(options: AppOptions, tasks: Vector[Task], logger: Logger): Unit =
    if tasks.isEmpty && options.mode != RunMode.Plan(OutputFormat.Json) then
      logger.warning("No tasks given in configuration, no work to do")

  private def resolveBaseDirectory(options: AppOptions, deps: AppDependencies, logger: Logger): Either[Int, String] =
    val base =
      if options.baseDirectory.isBlank then PathUtil.dirname(PathUtil.abs(options.configFiles.head))
      else PathUtil.abs(options.baseDirectory)

    deps.fs.stat(base) match
      case Left(error) =>
        logger.error(s"nonexistent base directory: ${error.getMessage}")
        Left(1)
      case Right(_) => Right(base)

  private def execute(
    options: AppOptions,
    stdout:  PrintStream,
    deps:    AppDependencies,
    logger:  Logger,
    loaded:  LoadedConfig,
  ): Int =
    val stylish = symbolsEnabled(options)
    val interpreter = Wiring.interpreter(loaded.base, options, logger, deps)
    val context = AppCommandContext(options, stdout, logger, interpreter, loaded, stylish)
    AppCommand.from(options.mode).execute(context)

  private def loggerFor(stdout: PrintStream, options: AppOptions): Logger =
    val colorSupport = ColorSupport.detect
    Logger(stdout, minimumLevel(options), colorEnabled(options, colorSupport), symbolsEnabled(options))

  private def minimumLevel(options: AppOptions): Level =
    if options.quiet then Level.Warning
    else if options.verbose == 1 then Level.Info
    else if options.verbose > 1 then Level.Debug
    else Level.Action

  private def colorEnabled(options: AppOptions, support: ColorSupport): Boolean =
    if options.forceColor && options.noColor then support.enabled
    else if options.forceColor then true
    else if options.noColor then false
    else support.enabled

  private def symbolsEnabled(options: AppOptions): Boolean =
    if options.forceEmoji && options.noEmoji then false
    else if options.forceEmoji then true
    else if options.noEmoji then false
    else SymbolSupport.detect.enabled
