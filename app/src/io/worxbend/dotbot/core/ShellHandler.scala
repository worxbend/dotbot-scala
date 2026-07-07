package io.worxbend.dotbot.core

final class ShellHandler extends BatchedDirectiveHandler[ShellSpec, ShellEntry]:

  def directive: Directive = Directive.Shell

  protected def decoder: ConfigDecoder[ShellSpec] =
    DirectiveDecoders.shellSpecDecoder

  override def plan(spec: ShellSpec): Vector[Operation] =
    spec.entries.collect { case ShellEntry.Command(command, description, _, _, _, _) =>
      Operation(Directive.Shell, command, description, DetailStyle.ShellDescription)
    }

  override protected def entries(spec: ShellSpec): Vector[ShellEntry] =
    spec.entries

  override protected def executeEntry(ctx: RuntimeContext, entry: ShellEntry): Outcome =
    entry match
      case ShellEntry.Invalid => Outcome.Failed
      case command: ShellEntry.Command => runCommand(ctx, command)

  override protected def allSuccessfulMessage: String =
    "All commands have been executed"

  override protected def someFailedMessage: String =
    "Some commands were not successfully executed"

  private def runCommand(ctx: RuntimeContext, command: ShellEntry.Command): Outcome =
    val prefix = if ctx.options.dryRun then "Would run command " else ""

    if command.quiet then
      if command.description.nonEmpty then ctx.log.info(prefix + command.description)
    else if command.description.isEmpty then ctx.log.action(prefix + command.command)
    else ctx.log.action(s"$prefix${command.description} [${command.command}]")

    if ctx.options.dryRun then Outcome.Ok
    else
      ctx.runShell(
        command.command,
        ShellOptions(
          cwd = ctx.baseDirectory,
          enableStdin = command.stdin,
          enableStdout = ctx.options.verbose > 1 || command.stdout,
          enableStderr = ctx.options.verbose > 1 || command.stderr,
          timeout = ctx.options.shellTimeout,
        ),
        _ => s"Command [${command.command}] failed",
      )
