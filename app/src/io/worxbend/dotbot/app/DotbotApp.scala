package io.worxbend.dotbot.app

import io.worxbend.dotbot.config.ConfigReader
import io.worxbend.dotbot.core.CoreOptions
import io.worxbend.dotbot.core.Dispatcher
import io.worxbend.dotbot.core.PlanOutput
import io.worxbend.dotbot.core.RuntimeContext
import io.worxbend.dotbot.core.PathUtil
import io.worxbend.dotbot.fs.Filesystem
import io.worxbend.dotbot.fs.OsFilesystem
import io.worxbend.dotbot.logging.Level
import io.worxbend.dotbot.logging.Logger
import io.worxbend.dotbot.shell.OsShellRunner
import io.worxbend.dotbot.shell.ShellRunner

import java.io.PrintStream
import java.time.Clock

final case class AppOptions(
  quiet:         Boolean = false,
  verbose:       Int = 0,
  baseDirectory: String = "",
  configFiles:   Vector[String] = Vector.empty,
  only:          Set[String] = Set.empty,
  skip:          Set[String] = Set.empty,
  dryRun:        Boolean = false,
  forceColor:    Boolean = false,
  noColor:       Boolean = false,
  exitOnFailure: Boolean = false,
  validate:      Boolean = false,
  plan:          Boolean = false,
  output:        String = "text",
)

final case class AppDependencies(
  reader: ConfigReader = ConfigReader(),
  fs:     Filesystem = OsFilesystem,
  shell:  ShellRunner = OsShellRunner,
  clock:  Clock = Clock.systemDefaultZone(),
)

object DotbotApp:
  val version: String = sys.env.getOrElse("DOTBOT_SCALA_VERSION", "0.1.0")

  def run(options: AppOptions, stdout: PrintStream = System.out, deps: AppDependencies = AppDependencies()): Int =
    val logger = Logger(stdout)
    configureLogger(logger, options)

    if options.forceColor && options.noColor then
      logger.error("`--force-color` and `--no-color` cannot both be provided")
      return 1
    if options.forceColor then logger.useColor(true)
    if options.noColor then logger.useColor(false)

    if options.configFiles.isEmpty then
      logger.error("No configuration file specified")
      return 1

    val tasks =
      deps.reader.read(options.configFiles) match
        case Left(error) =>
          logger.error(error)
          return 1
        case Right(value) => value

    if tasks.isEmpty && !(options.plan && options.output == "json") then
      logger.warning("No tasks given in configuration, no work to do")

    val base =
      if options.baseDirectory.isBlank then PathUtil.dirname(PathUtil.abs(options.configFiles.head))
      else PathUtil.abs(options.baseDirectory)

    deps.fs.stat(base) match
      case Left(error) =>
        logger.error(s"nonexistent base directory: ${error.getMessage}")
        return 1
      case Right(_) =>

    val coreOptions = CoreOptions(
      only = options.only,
      skip = options.skip,
      exitOnFailure = options.exitOnFailure,
      dryRun = options.dryRun,
      verbose = options.verbose,
    )

    val ctx = RuntimeContext(base, coreOptions, logger, deps.fs, deps.shell, deps.clock)
    val dispatcher = Dispatcher(ctx, Dispatcher.builtIns)

    if options.validate || options.plan then
      dispatcher.plan(tasks) match
        case Left(error) =>
          logger.error(error)
          1
        case Right(plan) =>
          if options.plan then
            options.output match
              case "" | "text" =>
                stdout.print(PlanOutput.text(plan, tasks.size, options.configFiles.size, base))
                0
              case "json" =>
                stdout.print(PlanOutput.json(plan, tasks.size, options.configFiles.size, base))
                0
              case other =>
                logger.error(s"unsupported output format \"$other\"")
                1
          else
            logger.action(
              s"Configuration is valid: ${tasks.size} task(s), ${options.configFiles.size} config file(s), ${plan.operations.size} planned operation(s), base $base",
            )
            0
    else
      val mode = if options.dryRun then "dry-run" else "apply"
      logger.action(s"Starting $mode with ${tasks.size} task(s), ${options.configFiles.size} config file(s), base $base")
      dispatcher.dispatch(tasks) match
        case Left(error) =>
          logger.error(error)
          1
        case Right(true) =>
          logger.info("All tasks executed successfully")
          0
        case Right(false) =>
          logger.error("Some tasks were not executed successfully")
          1

  private def configureLogger(logger: Logger, options: AppOptions): Unit =
    if options.quiet then logger.setLevel(Level.Warning)
    if options.verbose > 0 then
      if options.verbose == 1 then logger.setLevel(Level.Info)
      else logger.setLevel(Level.Debug)
