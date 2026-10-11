package io.worxbend.dotbot.core

final class CleanHandler extends BatchedDirectiveHandler[CleanSpec, CleanEntry]:

  def directive: Directive = Directive.Clean

  protected def decoder: ConfigDecoder[CleanSpec] =
    DirectiveDecoders.cleanSpecDecoder

  override def plan(spec: CleanSpec): Vector[Operation] =
    spec.entries.collect { case CleanEntry.Target(target, _) => Operation(Directive.Clean, target) }

  protected override def entries(spec: CleanSpec): Vector[CleanEntry] =
    spec.entries

  protected override def executeEntry(ctx: RuntimeContext, entry: CleanEntry): Outcome =
    entry match
      case CleanEntry.Target(target, options) =>
        cleanDirectory(ctx, ctx.paths.absFrom(ctx.baseDirectory, target), target, options)
      case CleanEntry.Invalid(description)    =>
        ctx.log.warning(s"Skipping clean entry that is not a path: $description")
        Outcome.Failed

  protected override def allSuccessfulMessage: String =
    "All targets have been cleaned"

  protected override def someFailedMessage: String =
    "Some targets were not successfully cleaned"

  /**
   * Clean one already-resolved directory.
   *
   * `dir` is an absolute path that has already been expanded; `reported` is the path as the user
   * wrote it, used only for log messages. The recursion below passes real directory entries back
   * in as `dir`, so expansion must not run again: a directory legitimately named `$cache` or `~`
   * is a name, not something to substitute.
   */
  private def cleanDirectory(
      ctx: RuntimeContext,
      dir: String,
      reported: String,
      options: CleanOptions,
  ): Outcome =
    if !ctx.fs.isDir(dir) then
      ctx.log.debug(s"Ignoring nonexistent directory $reported")
      Outcome.Ok
    else
      ctx.withFilesystem(ctx.fs.listDir(dir), _ => s"Failed to list directory $dir").fold(Outcome.Failed) { names =>
        names.foldLeft(Outcome.Ok) { (outcome, name) =>
          val path             = PathUtil.join(dir, name)
          val recursiveOutcome =
            if options.recursive && ctx.fs.isDir(path) && !ctx.fs.isSymlink(path) then
              cleanDirectory(ctx, path, path, options)
            else Outcome.Ok

          val removeOutcome =
            if !ctx.fs.exists(path) && ctx.fs.isSymlink(path) then removeBrokenLink(ctx, path, options.force)
            else Outcome.Ok

          outcome.combine(recursiveOutcome).combine(removeOutcome)
        }
      }

  private def removeBrokenLink(ctx: RuntimeContext, path: String, force: Boolean): Outcome =
    ctx.withFilesystem(ctx.fs.readlink(path), _ => s"Failed to inspect invalid link $path") match
      case None             => Outcome.Failed
      case Some(targetPath) =>
        val pointsAt = PathUtil.resolveLinkTarget(path, targetPath)
        if PathUtil.contains(ctx.baseDirectory, pointsAt) || force then
          if ctx.options.dryRun then
            ctx.log.action(s"Would remove invalid link $path -> $pointsAt")
            Outcome.Ok
          else
            ctx.log.action(s"Removing invalid link $path -> $pointsAt")
            if ctx.withFilesystem(ctx.fs.remove(path), _ => s"Failed to remove invalid link $path").isEmpty then
              Outcome.Failed
            else Outcome.Ok
        else
          ctx.log.info(s"Link $path -> $pointsAt not removed.")
          Outcome.Ok
