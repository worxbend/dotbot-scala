package io.worxbend.dotbot.core

trait DirectiveHandler:
  type Spec <: DirectiveSpec

  def directive: Directive

  def supportsDryRun: Boolean = true

  protected def decoder: ConfigDecoder[Spec]

  final def decode(data: ConfigValue, defaults: DirectiveDefaults, mode: DecodeMode): Either[DotbotError, Spec] =
    decoder.decode(data, defaults, mode)

  def plan(spec: Spec): Vector[Operation]
  def execute(ctx: RuntimeContext, spec: Spec): Outcome

trait BatchedDirectiveHandler[S <: DirectiveSpec, E] extends DirectiveHandler:
  type Spec = S

  protected def entries(spec: S): Vector[E]
  protected def executeEntry(ctx: RuntimeContext, entry: E): Outcome
  protected def allSuccessfulMessage: String
  protected def someFailedMessage: String

  def execute(ctx: RuntimeContext, spec: S): Outcome =
    entries(spec)
      .foldLeft(Outcome.Ok) { (outcome, entry) =>
        outcome.combine(executeEntry(ctx, entry))
      }
      .withSummary(ctx.log, allSuccessfulMessage, someFailedMessage)
