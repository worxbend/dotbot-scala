package io.worxbend.dotbot.core

final class CreateHandler extends BatchedDirectiveHandler[CreateSpec, CreateEntry]:

  def directive: Directive = Directive.Create

  protected def decoder: ConfigDecoder[CreateSpec] =
    DirectiveDecoders.createSpecDecoder

  override def plan(spec: CreateSpec): Vector[Operation] =
    spec.entries.collect { case CreateEntry.Path(path, _) => Operation(Directive.Create, path) }

  override protected def entries(spec: CreateSpec): Vector[CreateEntry] =
    spec.entries

  override protected def executeEntry(ctx: RuntimeContext, entry: CreateEntry): Outcome =
    entry match
      case CreateEntry.Path(path, mode) => createPath(ctx, path, mode)
      case CreateEntry.Invalid          => Outcome.Failed

  override protected def allSuccessfulMessage: String =
    "All paths have been set up"

  override protected def someFailedMessage: String =
    "Some paths were not successfully set up"

  private def createPath(ctx: RuntimeContext, path: String, mode: FileMode): Outcome =
    val absolute = PathUtil.absFrom(ctx.baseDirectory, path)
    if ctx.fs.exists(absolute) then
      ctx.log.info(s"Path exists $absolute")
      Outcome.Ok
    else if ctx.options.dryRun then
      ctx.log.action(s"Would create path $absolute")
      Outcome.Ok
    else
      ctx.log.action(s"Creating path $absolute")
      if ctx.withFilesystem(ctx.fs.mkdirAll(absolute, mode), _ => s"Failed to create path $absolute").isEmpty then Outcome.Failed
      else if ctx.withFilesystem(ctx.fs.chmod(absolute, mode), _ => s"Failed to set mode for path $absolute").isEmpty then Outcome.Failed
      else Outcome.Ok
