package io.worxbend.dotbot.app

import io.worxbend.dotbot.core.Interpreter
import io.worxbend.dotbot.core.Task
import io.worxbend.dotbot.logging.Logger

import java.io.PrintStream

/**
 * Loaded and validated configuration payload shared by command execution.
 */
private[app] final case class LoadedConfig(tasks: Vector[Task], base: String)

/**
 * Shared execution context for command handlers.
 */
private[app] final case class AppCommandContext(
  options:    AppOptions,
  stdout:     PrintStream,
  logger:     Logger,
  interpreter: Interpreter,
  loaded:     LoadedConfig,
  stylishUi:  Boolean,
)

/**
 * Discrete runtime command modes selected from parsed CLI mode.
 */
private[app] enum AppCommand:
  case Apply
  case Validate
  case Plan(format: OutputFormat)
  case InvalidPlanOutput(value: String)

  /**
   * Execute this command and return a process exit code.
   */
  def execute(ctx: AppCommandContext): Int =
    this match
      case AppCommand.Apply                   => applyConfig(ctx)
      case AppCommand.Validate                => validateConfig(ctx)
      case AppCommand.Plan(format)            => printPlan(ctx, format)
      case AppCommand.InvalidPlanOutput(value) => invalidPlanOutput(ctx, value)

  private def applyConfig(ctx: AppCommandContext): Int =
    val mode = if ctx.options.dryRun then "dry-run" else "apply"
    ctx.logger.action(
      s"Starting $mode with ${ctx.loaded.tasks.size} task(s), ${ctx.options.configFiles.size} config file(s), base ${ctx.loaded.base}",
    )
    ctx.interpreter.dispatch(ctx.loaded.tasks) match
      case Left(error) =>
        ctx.logger.error(error.render)
        1
      case Right(outcome) if outcome.successful =>
        ctx.logger.info("All tasks executed successfully")
        0
      case Right(_) =>
        ctx.logger.error("Some tasks were not executed successfully")
        1

  private def validateConfig(ctx: AppCommandContext): Int =
    ctx.interpreter.plan(ctx.loaded.tasks) match
      case Left(error) =>
        ctx.logger.error(error.render)
        1
      case Right(plan) =>
        ctx.logger.action(
          s"Configuration is valid: ${ctx.loaded.tasks.size} task(s), ${ctx.options.configFiles.size} config file(s), ${plan.operations.size} planned operation(s), base ${ctx.loaded.base}",
        )
        0

  private def printPlan(ctx: AppCommandContext, format: OutputFormat): Int =
    ctx.interpreter.plan(ctx.loaded.tasks) match
      case Left(error) =>
        ctx.logger.error(error.render)
        1
      case Right(plan) =>
        format match
          case OutputFormat.Text =>
            ctx.stdout.print(
              PlanOutput.text(plan, ctx.loaded.tasks.size, ctx.options.configFiles.size, ctx.loaded.base, ctx.stylishUi),
            )
            0
          case OutputFormat.Json =>
            ctx.stdout.print(PlanOutput.json(plan, ctx.loaded.tasks.size, ctx.options.configFiles.size, ctx.loaded.base))
            0

  private def invalidPlanOutput(ctx: AppCommandContext, value: String): Int =
    ctx.interpreter.plan(ctx.loaded.tasks) match
      case Left(error) =>
        ctx.logger.error(error.render)
        1
      case Right(_) =>
        ctx.logger.error(s"unsupported output format \"$value\"")
        1

/**
 * Command construction helpers.
 */
private[app] object AppCommand:
  /**
   * Build command variant from a `RunMode`.
   */
  def from(mode: RunMode): AppCommand =
    mode match
      case RunMode.Apply                    => AppCommand.Apply
      case RunMode.Validate                 => AppCommand.Validate
      case RunMode.Plan(format)             => AppCommand.Plan(format)
      case RunMode.InvalidPlanOutput(value) => AppCommand.InvalidPlanOutput(value)
