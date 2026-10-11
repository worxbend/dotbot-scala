package io.worxbend.dotbot.core

final class ShellHandler extends BatchedDirectiveHandler[ShellSpec, ShellEntry]:

  def directive: Directive = Directive.Shell

  protected def decoder: ConfigDecoder[ShellSpec] =
    DirectiveDecoders.shellSpecDecoder

  override def plan(spec: ShellSpec): Vector[Operation] =
    spec.entries.collect { case ShellEntry.Command(command, description, _) =>
      Operation(Directive.Shell, command, description, DetailStyle.ShellDescription)
    }

  protected override def entries(spec: ShellSpec): Vector[ShellEntry] =
    spec.entries

  protected override def executeEntry(ctx: RuntimeContext, entry: ShellEntry): Outcome =
    entry match
      case ShellEntry.Invalid(description) =>
        ctx.log.warning(s"Skipping shell entry without a command: $description")
        Outcome.Failed
      case command: ShellEntry.Command     => runCommand(ctx, command)

  protected override def allSuccessfulMessage: String =
    "All commands have been executed"

  protected override def someFailedMessage: String =
    "Some commands were not successfully executed"

  private def runCommand(ctx: RuntimeContext, command: ShellEntry.Command): Outcome =
    val prefix = if ctx.options.dryRun then "Would run command " else ""

    if command.options.quiet then
      if command.description.nonEmpty then ctx.log.info(prefix + command.description)
    else if command.description.isEmpty then ctx.log.action(prefix + command.command)
    else ctx.log.action(s"$prefix${command.description} [${command.command}]")

    if ctx.options.dryRun then Outcome.Ok
    else
      ctx.runShell(
        command.command,
        ShellOptions(
          cwd = ctx.baseDirectory,
          enableStdin = command.options.stdin,
          enableStdout = ctx.options.verbose > 1 || command.options.stdout,
          enableStderr = ctx.options.verbose > 1 || command.options.stderr,
          timeout = ctx.options.shellTimeout,
        ),
        _ => s"Command [${command.command}] failed",
      )
