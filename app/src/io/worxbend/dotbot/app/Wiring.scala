package io.worxbend.dotbot.app

import io.worxbend.dotbot.core.CoreOptions
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DirectiveRegistry
import io.worxbend.dotbot.core.DirectiveHandler
import io.worxbend.dotbot.core.Interpreter
import io.worxbend.dotbot.core.RuntimeContext
import io.worxbend.dotbot.logging.Logger

private[app] object Wiring:
  def interpreter(base: String, options: AppOptions, logger: Logger, deps: AppDependencies): Interpreter =
    Interpreter(RuntimeContext(base, coreOptions(options), logger, deps.fs, deps.shell, deps.clock), handlers)

  def handlers: Map[Directive, DirectiveHandler] =
    DirectiveRegistry.handlers

  private def coreOptions(options: AppOptions): CoreOptions =
    CoreOptions(
      only = options.only,
      skip = options.skip,
      exitOnFailure = options.exitOnFailure,
      dryRun = options.dryRun,
      verbose = options.verbose,
    )
