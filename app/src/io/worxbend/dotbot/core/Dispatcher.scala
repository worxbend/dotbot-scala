package io.worxbend.dotbot.core

import io.worxbend.dotbot.config.ConfigValue
import io.worxbend.dotbot.config.Task
import io.worxbend.dotbot.fs.Filesystem
import io.worxbend.dotbot.logging.Logger
import io.worxbend.dotbot.shell.ShellRunner

import java.time.Clock
import java.time.Duration

import scala.util.boundary
import scala.util.boundary.break

final case class CoreOptions(
  only:          Set[String] = Set.empty,
  skip:          Set[String] = Set.empty,
  exitOnFailure: Boolean = false,
  dryRun:        Boolean = false,
  verbose:       Int = 0,
  shellTimeout:  Duration = Duration.ofMinutes(10),
)

final class RuntimeContext(
  val baseDirectory: String,
  val options:       CoreOptions,
  val log:           Logger,
  val fs:            Filesystem,
  val shell:         ShellRunner,
  val clock:         Clock,
):
  var defaults: Map[String, ConfigValue] = Map.empty

trait Handler:
  def canHandle(directive: String): Boolean

  def supportsDryRun: Boolean = true

  def validate(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Unit] = Right(())

  def plan(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Vector[Operation]] =
    validate(ctx, directive, data).map(_ => Vector(Operation(directive)))

  def handle(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Boolean]

final class Dispatcher(ctx: RuntimeContext, handlers: Vector[Handler]):
  def dispatch(tasks: Vector[Task]): Either[String, Boolean] =
    boundary:
      var success = true
      for task <- tasks do
        for action <- task.actions do
          val directive = action.directive
          val data = action.data
          if shouldSkip(directive) && directive != "defaults" then ctx.log.info(s"Skipping action $directive")
          else if directive == "defaults" then
            ctx.defaults = data.asMap.getOrElse(Map.empty)
          else
            val handled = handlers.filter(_.canHandle(directive))
            if handled.isEmpty then
              ctx.log.error(s"Action $directive not handled")
              if ctx.options.exitOnFailure then break(Right(false))
              success = false
            else
              for handler <- handled do
                if ctx.options.dryRun && !handler.supportsDryRun then
                  ctx.log.action(s"Skipping dry-run-unaware handler ${handler.getClass.getSimpleName}")
                else
                  handler.handle(ctx, directive, data) match
                    case Left(error) =>
                      if ctx.options.exitOnFailure then break(Left(s"executing action $directive: $error"))
                      ctx.log.error(s"An error was encountered while executing action $directive")
                      ctx.log.debug(error)
                      success = false
                    case Right(localSuccess) =>
                      if !localSuccess && ctx.options.exitOnFailure then
                        ctx.log.error(s"Action $directive failed")
                        break(Right(false))
                      success = success && localSuccess
      Right(success)

  def plan(tasks: Vector[Task]): Either[String, Plan] =
    boundary:
      ctx.defaults = Map.empty
      val operations = Vector.newBuilder[Operation]
      for task <- tasks do
        for action <- task.actions do
          val directive = action.directive
          val data = action.data
          if shouldSkip(directive) && directive != "defaults" then ()
          else if directive == "defaults" then ctx.defaults = data.asMap.getOrElse(Map.empty)
          else
            handlerFor(directive) match
              case None => break(Left(s"action $directive not handled"))
              case Some(handler) =>
                handler.validate(ctx, directive, data).left.map(error => s"validating action $directive: $error") match
                  case Left(error) => break(Left(error))
                  case Right(_)    =>
                handler.plan(ctx, directive, data).left.map(error => s"planning action $directive: $error") match
                  case Left(error)  => break(Left(error))
                  case Right(items) => operations ++= items
      Right(Plan(operations.result()))

  private def handlerFor(directive: String): Option[Handler] =
    handlers.find(_.canHandle(directive))

  private def shouldSkip(directive: String): Boolean =
    (ctx.options.only.nonEmpty && !ctx.options.only.contains(directive)) || ctx.options.skip.contains(directive)

object Dispatcher:
  def builtIns: Vector[Handler] =
    Vector(CreateHandler, CleanHandler, LinkHandler, ShellHandler)
