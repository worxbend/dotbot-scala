package io.worxbend.dotbot.app

import io.worxbend.dotbot.core.Interpreter
import io.worxbend.dotbot.core.Plan as CorePlan
import io.worxbend.dotbot.core.Task
import io.worxbend.dotbot.logging.Logger

import java.io.PrintStream

/**
 * Loaded and validated configuration payload shared by command execution.
 */
final private[app] case class LoadedConfig(tasks: Vector[Task], base: String)

/**
 * Shared execution context for command handlers.
 */
final private[app] case class AppCommandContext(
    options: AppOptions,
    stdout: PrintStream,
    logger: Logger,
    interpreter: Interpreter,
    loaded: LoadedConfig,
    stylishUi: Boolean,
)

/**
 * Runs the command the user asked for and reports the process exit code.
 *
 * The choice of command is `RunMode`, which the CLI layer produces from the parsed arguments;
 * this object is only the "what to do about it" half. Keeping the dispatch here rather than as a
 * method on `RunMode` keeps that enum free of the interpreter, logger, and stream types -- it
 * stays plain data describing what was asked for.
 */
private[app] object AppCommand:
  def execute(mode: RunMode, ctx: AppCommandContext): Int =
    mode match
      case RunMode.Apply                    => applyConfig(ctx)
      case RunMode.Validate                 => validateConfig(ctx)
      case RunMode.Plan(format)             => printPlan(ctx, format)
      case RunMode.InvalidPlanOutput(value) => invalidPlanOutput(ctx, value)

  private def applyConfig(ctx: AppCommandContext): Int =
    val mode = if ctx.options.dryRun then "dry-run" else "apply"
    ctx.logger.action(
      s"Starting $mode with ${ctx.loaded.tasks.size} task(s), ${ctx.options.configFiles.size} config file(s), base ${ctx.loaded.base}",
    )
    ctx.interpreter.dispatch(ctx.loaded.tasks) match
      case Left(error)                          =>
        ctx.logger.error(error.render)
        1
      case Right(outcome) if outcome.successful =>
        ctx.logger.info("All tasks executed successfully")
        0
      case Right(_)                             =>
        ctx.logger.error("Some tasks were not executed successfully")
        1

  private def validateConfig(ctx: AppCommandContext): Int =
    withPlan(ctx) { plan =>
      ctx.logger.action(
        s"Configuration is valid: ${ctx.loaded.tasks.size} task(s), ${ctx.options.configFiles.size} config file(s), ${plan.operations.size} planned operation(s), base ${ctx.loaded.base}",
      )
      0
    }

  private def printPlan(ctx: AppCommandContext, format: OutputFormat): Int =
    withPlan(ctx) { plan =>
      format match
        case OutputFormat.Text =>
          ctx.stdout.print(
            PlanOutput.text(plan, ctx.loaded.tasks.size, ctx.options.configFiles.size, ctx.loaded.base, ctx.stylishUi),
          )
        case OutputFormat.Json =>
          ctx.stdout.print(PlanOutput.json(plan, ctx.loaded.tasks.size, ctx.options.configFiles.size, ctx.loaded.base))
      0
    }

  private def invalidPlanOutput(ctx: AppCommandContext, value: String): Int =
    // The config is still checked first, so a run that is wrong in both ways says so. The format
    // argument is then always reported, because it is wrong whatever the config turned out to be;
    // reporting only the config error left the user re-running a fixed config to discover the
    // typo in their `--output` value.
    ctx.interpreter.plan(ctx.loaded.tasks).left.foreach(error => ctx.logger.error(error.render))
    ctx.logger.error(s"unsupported output format \"$value\"")
    1

  /** Build the plan, reporting a failure as an error and exit code 1, or hand it to `render`. */
  private def withPlan(ctx: AppCommandContext)(render: CorePlan => Int): Int =
    ctx.interpreter.plan(ctx.loaded.tasks) match
      case Left(error) =>
        ctx.logger.error(error.render)
        1
      case Right(plan) => render(plan)
