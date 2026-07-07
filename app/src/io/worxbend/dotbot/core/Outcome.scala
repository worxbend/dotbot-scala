package io.worxbend.dotbot.core

enum Outcome:
  case Ok
  case Failed

  def successful: Boolean =
    this == Outcome.Ok

  def combine(other: Outcome): Outcome =
    if successful && other.successful then Outcome.Ok else Outcome.Failed

  def withSummary(ctx: RuntimeContext, okMessage: String, failMessage: String): Outcome =
    if successful then ctx.log.info(okMessage) else ctx.log.error(failMessage)
    this
