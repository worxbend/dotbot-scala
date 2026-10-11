package io.worxbend.dotbot.core

final class LinkHandler extends BatchedDirectiveHandler[LinkSpec, LinkEntry]:

  def directive: Directive = Directive.Link

  protected def decoder: ConfigDecoder[LinkSpec] =
    DirectiveDecoders.linkSpecDecoder

  override def plan(spec: LinkSpec): Vector[Operation] =
    spec.entries.collect { case LinkEntry.Link(linkName, target, _) =>
      Operation(Directive.Link, linkName, target, DetailStyle.LinkTarget)
    }

  override def execute(ctx: RuntimeContext, spec: LinkSpec): Outcome =
    spec match
      case LinkSpec(Some(linkType), _) =>
        ctx.log.warning(s"The default link type is not recognized: '${linkType.render}'")
        Outcome.Failed
      case LinkSpec(None, _)           => super.execute(ctx, spec)

  protected override def entries(spec: LinkSpec): Vector[LinkEntry] =
    spec.entries

  protected override def executeEntry(ctx: RuntimeContext, entry: LinkEntry): Outcome =
    entry match
      case LinkEntry.Link(rawLinkName, target, options) => handleOneLink(ctx, rawLinkName, target, options)
      case LinkEntry.InvalidLinkType(linkType)          =>
        ctx.log.warning(s"The link type is not recognized: '${linkType.render}'")
        Outcome.Failed

  protected override def allSuccessfulMessage: String =
    "All links have been set up"

  protected override def someFailedMessage: String =
    "Some links were not successfully set up"

  private def handleOneLink(
      ctx: RuntimeContext,
      rawLinkName: String,
      rawPath: String,
      options: LinkOptions,
  ): Outcome =
    val linkName   = ctx.paths.path(rawLinkName)
    val sourcePath = PathUtil.clean(ctx.paths.path(rawPath))
    conditionAllowsLink(ctx, options) match
      case Left(error)  =>
        ctx.log.warning(error.render)
        Outcome.Failed
      case Right(false) =>
        ctx.log.info(s"Skipping $linkName")
        Outcome.Ok
      case Right(true)  => processLinkSource(ctx, sourcePath, linkName, options)

  /**
   * Decide whether an entry's `if` condition permits the link.
   *
   * The condition is a shell command, so running it is a side effect like any other and
   * `--dry-run` must not do it: inspecting an unfamiliar dotfiles repository with the flag whose
   * whole purpose is to be safe used to execute whatever that repository's `if` conditions said.
   * A dry run instead reports the command and assumes it would succeed, which makes the plan show
   * every link the config could create.
   */
  private def conditionAllowsLink(ctx: RuntimeContext, options: LinkOptions): Either[DotbotError, Boolean] =
    if options.ifCommand.isEmpty then Right(true)
    else if !ctx.performsSideEffects then
      ctx.log.action(s"Would check condition [${options.ifCommand}]")
      Right(true)
    else
      ctx.shell
        .run(options.ifCommand, ShellOptions(cwd = ctx.baseDirectory, timeout = ctx.options.shellTimeout)) match
        case ShellExit.Completed(code)       => Right(code == 0)
        case ShellExit.TimedOut              => Left(DotbotError.Message(s"Link condition timed out [${options.ifCommand}]"))
        case ShellExit.FailedToStart(detail) => Left(DotbotError.Message(s"Could not start link condition: $detail"))

  private def processLinkSource(
      ctx: RuntimeContext,
      sourcePath: String,
      linkName: String,
      options: LinkOptions,
  ): Outcome =
    if options.glob && Glob.hasGlobChars(sourcePath) then processGlobbedLinks(ctx, sourcePath, linkName, options)
    else processOneLink(ctx, sourcePath, linkName, options, globbed = false)

  private def processGlobbedLinks(
      ctx: RuntimeContext,
      sourcePath: String,
      linkName: String,
      options: LinkOptions,
  ): Outcome =
    val pattern  = ctx.paths.absFromExpanded(ctx.baseDirectory, sourcePath)
    val excludes = options.exclude.map(item => ctx.paths.absFrom(ctx.baseDirectory, item))
    Glob.createGlobResults(ctx.fs, pattern, excludes) match
      case Left(error)    =>
        ctx.log.warning(s"Unable to expand glob '$sourcePath': ${error.render}")
        Outcome.Failed
      case Right(matches) =>
        ctx.log.debug(s"Globs from '$sourcePath': ${formatStrings(matches)}")
        matches.foldLeft(Outcome.Ok) { (outcome, fullItem) =>
          outcome.combine(processGlobMatch(ctx, pattern, fullItem, linkName, options))
        }

  private def processGlobMatch(
      ctx: RuntimeContext,
      pattern: String,
      fullItem: String,
      linkName: String,
      options: LinkOptions,
  ): Outcome =
    val itemName     =
      val raw = Glob.globLinkItem(pattern, fullItem)
      if options.prefix.nonEmpty then options.prefix + raw else raw
    val globLinkName = PathUtil.join(linkName, itemName)
    processOneLink(ctx, fullItem, globLinkName, options, globbed = true)

  private def processOneLink(
      ctx: RuntimeContext,
      target: String,
      linkName: String,
      options: LinkOptions,
      globbed: Boolean,
  ): Outcome =
    val link = resolveLink(ctx, target, linkName, options)
    // The missing-target check comes before `createParent`, which really does create directories.
    // With the two the other way round, a link whose source does not exist still left an empty
    // directory tree behind: the run reported failure but had already changed the filesystem, and
    // neither a re-run nor `--dry-run` predicted it.
    if !globbed && !options.ignoreMissing && !ctx.fs.exists(link.absoluteTarget) then
      ctx.log.warning(s"Nonexistent target ${link.linkName} -> ${link.target}")
      Outcome.Failed
    else
      LinkDestination(ctx, link).prepare(options) match
        case DestinationPreparation.Failed => Outcome.Failed
        case prepared                      => createLink(ctx, link, options, prepared == DestinationPreparation.Available)

  private def resolveLink(ctx: RuntimeContext, target: String, linkName: String, options: LinkOptions): LinkResolution =
    val base           = baseDir(ctx, options.canonicalize)
    val absoluteTarget = ctx.paths.absFromExpanded(base, target)
    val linkPath       = ctx.paths.absFromExpanded(ctx.baseDirectory, linkName)
    val targetPath     =
      if options.relative then PathUtil.relative(PathUtil.dirname(linkPath), absoluteTarget)
      else absoluteTarget
    LinkResolution(target, linkName, linkPath, absoluteTarget, targetPath)

  private def baseDir(ctx: RuntimeContext, canonical: Boolean): String =
    if !canonical then ctx.baseDirectory
    else
      ctx
        .withFilesystem(ctx.fs.realpath(ctx.baseDirectory), _ => "Failed to resolve base directory")
        .getOrElse(ctx.baseDirectory)

  private def formatStrings(values: Vector[String]): String =
    values.map(value => "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").mkString("Vector(", ", ", ")")

  /**
   * Put the link in place, or explain why what is already there is in the way.
   *
   * Three cases, in the order they are tested. The link path is free, so create the link; there is
   * already a symlink there, so compare it against what was wanted; or there is a real file or
   * directory there, which is only acceptable when it is already the same file as the target.
   */
  private def createLink(
      ctx: RuntimeContext,
      link: LinkResolution,
      options: LinkOptions,
      assumeLinkPathAvailable: Boolean,
  ): Outcome =
    if canCreateAtLinkPath(ctx, link, options, assumeLinkPathAvailable) then createNewLink(ctx, link, options)
    else if ctx.fs.isSymlink(link.linkPath) then resolveExistingSymlink(ctx, link, options)
    else resolveExistingFile(ctx, link, options)

  /**
   * Whether the link can be created: nothing is occupying the link path, and the target is there.
   *
   * Both `||` here are load-bearing as short-circuits, because the right-hand sides touch the
   * filesystem. `ctx.fs.exists` is only asked once the link path is known to be free, and it is
   * skipped entirely when `ignoreMissing` says a missing target is not an objection.
   */
  private def canCreateAtLinkPath(
      ctx: RuntimeContext,
      link: LinkResolution,
      options: LinkOptions,
      assumeLinkPathAvailable: Boolean,
  ): Boolean =
    val linkPathFree = !ctx.fs.lexists(link.linkPath) || (ctx.options.dryRun && assumeLinkPathAvailable)
    linkPathFree && (options.ignoreMissing || ctx.fs.exists(link.absoluteTarget))

  private def createNewLink(ctx: RuntimeContext, link: LinkResolution, options: LinkOptions): Outcome =
    if ctx.options.dryRun then
      ctx.log.action(s"Would create ${options.linkType.label} ${link.cleanLinkName} -> ${link.targetPath}")
      Outcome.Ok
    else
      val result =
        if options.linkType == LinkType.Symlink then ctx.fs.symlink(link.targetPath, link.linkPath)
        else ctx.fs.hardlink(link.absoluteTarget, link.linkPath)
      if ctx.withFilesystem(result, _ => s"Linking failed ${link.cleanLinkName} -> ${link.targetPath}").isEmpty then
        Outcome.Failed
      else
        ctx.log.action(s"Creating ${options.linkType.label} ${link.cleanLinkName} -> ${link.targetPath}")
        Outcome.Ok

  /** A symlink already occupies the link path: accept it only if it points where this entry wants. */
  private def resolveExistingSymlink(ctx: RuntimeContext, link: LinkResolution, options: LinkOptions): Outcome =
    if options.linkType != LinkType.Symlink then
      ctx.log.warning(s"${link.cleanLinkName} already exists but is a symbolic link, not a hard link")
      Outcome.Failed
    else
      ctx.withFilesystem(ctx.fs.readlink(link.linkPath), _ => s"Failed to inspect link ${link.cleanLinkName}") match
        case None                                        =>
          Outcome.Failed
        case Some(current) if current == link.targetPath =>
          ctx.log.info(s"Link exists ${link.cleanLinkName} -> ${link.targetPath}")
          Outcome.Ok
        case Some(current)                               =>
          // "Invalid" means the link is dangling, "Incorrect" that it resolves but to the wrong place.
          val term = if !ctx.fs.exists(link.linkPath) then "Invalid" else "Incorrect"
          ctx.log.warning(s"$term link ${link.cleanLinkName} -> $current")
          Outcome.Failed

  /**
   * A real file or directory already occupies the link path.
   *
   * The one acceptable case is a hard link that has already been made: the path and the target are
   * then literally the same file, so there is nothing left to do. `&&` short-circuits, so the
   * `sameFile` probe is never run for a symlink-type entry.
   */
  private def resolveExistingFile(ctx: RuntimeContext, link: LinkResolution, options: LinkOptions): Outcome =
    val alreadyHardlinked =
      options.linkType == LinkType.Hardlink && ctx
        .withFilesystem(
          ctx.fs.sameFile(link.linkPath, link.absoluteTarget),
          _ => s"Failed to compare file links for ${link.cleanLinkName}",
        )
        .contains(true)

    if alreadyHardlinked then
      ctx.log.info(s"Link exists ${link.cleanLinkName} -> ${link.targetPath}")
      Outcome.Ok
    else
      ctx.log.warning(s"${link.cleanLinkName} already exists but is a regular file or directory")
      Outcome.Failed
