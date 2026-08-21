package io.worxbend.dotbot.core

import scala.util.boundary
import scala.util.boundary.break

final class Interpreter(ctx: RuntimeContext, handlers: Map[Directive, DirectiveHandler]):
  def dispatch(tasks: Vector[Task]): Either[DotbotError, Outcome] =
    boundary:
      val (outcome, _) =
        actions(tasks).foldLeft(Outcome.Ok -> DirectiveDefaults.empty) { case ((outcome, defaults), action) =>
          dispatchAction(outcome, defaults, action)
        }
      Right(outcome)

  def plan(tasks: Vector[Task]): Either[DotbotError, Plan] =
    for resolved <- resolvePlan(tasks)
    yield Plan(resolved.flatMap(_.plan))

  private trait ResolvedAction:
    def plan: Vector[Operation]
    def execute(ctx: RuntimeContext): Outcome

  private object ResolvedAction:
    def apply(handler: DirectiveHandler)(spec: handler.Spec): ResolvedAction =
      new ResolvedAction:
        def plan: Vector[Operation] =
          handler.plan(spec)

        def execute(ctx: RuntimeContext): Outcome =
          handler.execute(ctx, spec)

  final private case class ResolveState(defaults: DirectiveDefaults, actions: List[ResolvedAction])

  private enum ActionStep:
    case Skip(directive: Directive)
    case UpdateDefaults(defaults: DirectiveDefaults)
    case MissingHandler(directive: Directive)
    case Handle(handler: DirectiveHandler)

  private def resolvePlan(tasks: Vector[Task]): Either[DotbotError, Vector[ResolvedAction]] =
    actions(tasks)
      .foldLeft(Right(ResolveState(DirectiveDefaults.empty, Nil)): Either[DotbotError, ResolveState]) { (acc, action) =>
        acc.flatMap(resolvePlanAction(_, action))
      }
      .map(_.actions.reverse.toVector)

  private def dispatchAction(
      outcome: Outcome,
      defaults: DirectiveDefaults,
      action: Action,
  )(using boundary.Label[Either[DotbotError, Outcome]]): (Outcome, DirectiveDefaults) =
    actionStep(action) match
      case ActionStep.Skip(directive)           =>
        ctx.log.info(s"Skipping action ${directive.label}")
        outcome -> defaults
      case ActionStep.UpdateDefaults(updated)   =>
        outcome -> updated
      case ActionStep.MissingHandler(directive) =>
        ctx.log.error(s"Action ${directive.label} not handled")
        if ctx.options.exitOnFailure then break(Right(Outcome.Failed): Either[DotbotError, Outcome])
        outcome.combine(Outcome.Failed) -> defaults
      case ActionStep.Handle(handler)           =>
        dispatchHandledAction(outcome, defaults, action, handler)

  private def dispatchHandledAction(
      outcome: Outcome,
      defaults: DirectiveDefaults,
      action: Action,
      handler: DirectiveHandler,
  )(using boundary.Label[Either[DotbotError, Outcome]]): (Outcome, DirectiveDefaults) =
    val directive = action.directive
    if ctx.options.dryRun && !handler.supportsDryRun then
      ctx.log.action(s"Skipping dry-run-unaware handler ${handler.getClass.getSimpleName}")
      outcome -> defaults
    else
      val localOutcome = resolveAction(handler, action.data, defaults, DecodeMode.Execute).map(_.execute(ctx))
      localOutcome match
        case Left(error) if ctx.options.exitOnFailure =>
          break(Left(DotbotError.Execution(directive, error)): Either[DotbotError, Outcome])
        case Left(error)                              =>
          ctx.log.error(s"An error was encountered while executing action ${directive.label}")
          ctx.log.debug(error.render)
          outcome.combine(Outcome.Failed) -> defaults
        case Right(localOutcome)                      =>
          if !localOutcome.successful && ctx.options.exitOnFailure then
            ctx.log.error(s"Action ${directive.label} failed")
            break(Right(Outcome.Failed): Either[DotbotError, Outcome])
          outcome.combine(localOutcome) -> defaults

  private def resolvePlanAction(state: ResolveState, action: Action): Either[DotbotError, ResolveState] =
    actionStep(action) match
      case ActionStep.Skip(_)                   =>
        Right(state)
      case ActionStep.UpdateDefaults(defaults)  =>
        Right(state.copy(defaults = defaults))
      case ActionStep.MissingHandler(directive) =>
        Left(DotbotError.ActionNotHandled(directive))
      case ActionStep.Handle(handler)           =>
        resolveAction(handler, action.data, state.defaults, DecodeMode.Validate).left
          .map(error => DotbotError.Validation(action.directive, error))
          .map(action => state.copy(actions = action :: state.actions))

  private def actionStep(action: Action): ActionStep =
    val directive = action.directive
    if shouldSkip(directive) && directive != Directive.Defaults then ActionStep.Skip(directive)
    else
      directive match
        case Directive.Defaults => ActionStep.UpdateDefaults(DirectiveDefaults.from(action.data))
        case _                  =>
          handlerFor(directive) match
            case None          => ActionStep.MissingHandler(directive)
            case Some(handler) => ActionStep.Handle(handler)

  private def resolveAction(
      handler: DirectiveHandler,
      data: ConfigValue,
      defaults: DirectiveDefaults,
      mode: DecodeMode,
  ): Either[DotbotError, ResolvedAction] =
    handler.decode(data, defaults, mode).map(spec => ResolvedAction(handler)(spec))

  private def actions(tasks: Vector[Task]): Iterator[Action] =
    tasks.iterator.flatMap(_.actions.iterator)

  private def handlerFor(directive: Directive): Option[DirectiveHandler] =
    handlers.get(directive)

  private def shouldSkip(directive: Directive): Boolean =
    (ctx.options.only.nonEmpty && !ctx.options.only.contains(directive)) || ctx.options.skip.contains(directive)
