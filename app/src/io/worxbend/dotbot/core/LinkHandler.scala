package io.worxbend.dotbot.core

import java.time.format.DateTimeFormatter

final class LinkHandler extends BatchedDirectiveHandler[LinkSpec, LinkEntry]:

  private val BackupTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

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
      case LinkSpec(None, _) => super.execute(ctx, spec)

  override protected def entries(spec: LinkSpec): Vector[LinkEntry] =
    spec.entries

  override protected def executeEntry(ctx: RuntimeContext, entry: LinkEntry): Outcome =
    entry match
      case LinkEntry.Link(rawLinkName, target, options) => handleOneLink(ctx, rawLinkName, target, options)
      case LinkEntry.InvalidLinkType(linkType) =>
        ctx.log.warning(s"The link type is not recognized: '${linkType.render}'")
        Outcome.Failed

  override protected def allSuccessfulMessage: String =
    "All links have been set up"

  override protected def someFailedMessage: String =
    "Some links were not successfully set up"

  private def handleOneLink(
    ctx:         RuntimeContext,
    rawLinkName: String,
    rawPath:     String,
    options:     LinkOptions,
  ): Outcome =
    val linkName = ctx.paths.path(rawLinkName)
    val sourcePath = PathUtil.clean(ctx.paths.path(rawPath))
    if !conditionAllowsLink(ctx, options) then
      ctx.log.info(s"Skipping $linkName")
      Outcome.Ok
    else processLinkSource(ctx, sourcePath, linkName, options)

  /**
   * Decide whether an entry's `if` condition permits the link.
   *
   * The condition is a shell command, so running it is a side effect like any other and
   * `--dry-run` must not do it: inspecting an unfamiliar dotfiles repository with the flag whose
   * whole purpose is to be safe used to execute whatever that repository's `if` conditions said.
   * A dry run instead reports the command and assumes it would succeed, which makes the plan show
   * every link the config could create.
   */
  private def conditionAllowsLink(ctx: RuntimeContext, options: LinkOptions): Boolean =
    if options.ifCommand.isEmpty then true
    else if !ctx.performsSideEffects then
      ctx.log.action(s"Would check condition [${options.ifCommand}]")
      true
    else
      ctx.shell.run(options.ifCommand, ShellOptions(cwd = ctx.baseDirectory, timeout = ctx.options.shellTimeout)).successful

  private def processLinkSource(
    ctx:        RuntimeContext,
    sourcePath: String,
    linkName:   String,
    options:    LinkOptions,
  ): Outcome =
    if options.glob && Glob.hasGlobChars(sourcePath) then processGlobbedLinks(ctx, sourcePath, linkName, options)
    else processOneLink(ctx, sourcePath, linkName, options, globbed = false)

  private def processGlobbedLinks(
    ctx:        RuntimeContext,
    sourcePath: String,
    linkName:   String,
    options:    LinkOptions,
  ): Outcome =
    val pattern = ctx.paths.absFromExpanded(ctx.baseDirectory, sourcePath)
    val excludes = options.exclude.map(item => ctx.paths.absFrom(ctx.baseDirectory, item))
    Glob.createGlobResults(ctx.fs, pattern, excludes) match
      case Left(error) =>
        ctx.log.warning(s"Unable to expand glob '$sourcePath': ${error.render}")
        Outcome.Failed
      case Right(matches) =>
        ctx.log.debug(s"Globs from '$sourcePath': ${formatStrings(matches)}")
        matches.foldLeft(Outcome.Ok) { (outcome, fullItem) =>
          outcome.combine(processGlobMatch(ctx, pattern, fullItem, linkName, options))
        }

  private def processGlobMatch(
    ctx:      RuntimeContext,
    pattern:  String,
    fullItem: String,
    linkName: String,
    options:  LinkOptions,
  ): Outcome =
    val itemName =
      val raw = Glob.globLinkItem(pattern, fullItem)
      if options.prefix.nonEmpty then options.prefix + raw else raw
    val globLinkName = PathUtil.join(linkName, itemName)
    processOneLink(ctx, fullItem, globLinkName, options, globbed = true)

  private def processOneLink(
    ctx:      RuntimeContext,
    target:   String,
    linkName: String,
    options:  LinkOptions,
    globbed:  Boolean,
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
      val parentOutcome =
        if options.create then createParent(ctx, link.linkPath)
        else Outcome.Ok
      val backupResult =
        if options.backup then backup(ctx, link)
        else BackupResult.NotNeeded

      val removeResult =
        if (options.force || options.relink) && backupResult != BackupResult.BackedUp then deleteLink(ctx, link, options)
        else RemoveResult.Kept

      val linkPathAvailable = backupResult.makesLinkPathAvailable || removeResult.makesLinkPathAvailable
      createLink(ctx, link, options, options.ignoreMissing, linkPathAvailable)
        .combine(parentOutcome)
        .combine(backupResult.outcome)
        .combine(removeResult.outcome)

  private final case class LinkResolution(
    target:         String,
    linkName:       String,
    linkPath:       String,
    absoluteTarget: String,
    targetPath:     String,
  ):
    def cleanLinkName: String = PathUtil.clean(linkName)

  private def resolveLink(ctx: RuntimeContext, target: String, linkName: String, options: LinkOptions): LinkResolution =
    val base = baseDir(ctx, options.canonicalize)
    val absoluteTarget = ctx.paths.absFromExpanded(base, target)
    val linkPath = ctx.paths.absFromExpanded(ctx.baseDirectory, linkName)
    val targetPath =
      if options.relative then PathUtil.relative(PathUtil.dirname(linkPath), absoluteTarget)
      else absoluteTarget
    LinkResolution(target, linkName, linkPath, absoluteTarget, targetPath)

  private def baseDir(ctx: RuntimeContext, canonical: Boolean): String =
    if !canonical then ctx.baseDirectory else ctx.withFilesystem(ctx.fs.realpath(ctx.baseDirectory), _ => "Failed to resolve base directory").getOrElse(ctx.baseDirectory)

  private def formatStrings(values: Vector[String]): String =
    values.map(value => "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").mkString("Vector(", ", ", ")")

  private def createParent(ctx: RuntimeContext, linkPath: String): Outcome =
    val parent = PathUtil.dirname(linkPath)
    if ctx.fs.exists(parent) then Outcome.Ok
    else if ctx.options.dryRun then
      ctx.log.action(s"Would create directory $parent")
      Outcome.Ok
    else
      if ctx.withFilesystem(ctx.fs.mkdirAll(parent), _ => s"Failed to create directory $parent").isEmpty then Outcome.Failed
      else
        ctx.log.action(s"Creating directory $parent")
        Outcome.Ok

  private enum BackupResult:
    def outcome: Outcome =
      this match
        case Failed => Outcome.Failed
        case _      => Outcome.Ok

    def makesLinkPathAvailable: Boolean =
      this == BackedUp

    case NotNeeded, BackedUp, Failed

  private enum RemoveResult:
    def outcome: Outcome =
      this match
        case FailedBeforeRemove | FailedAfterRemoveAttempt => Outcome.Failed
        case _                                             => Outcome.Ok

    def makesLinkPathAvailable: Boolean =
      this match
        case Removed => true
        case _       => false

    case Kept, Removed, FailedBeforeRemove, FailedAfterRemoveAttempt

  private enum RemoveDecision:
    case Keep, Remove

  private def backup(ctx: RuntimeContext, link: LinkResolution): BackupResult =
    if ctx.fs.exists(link.linkPath) && !ctx.fs.isSymlink(link.linkPath) then
      val timestamp = BackupTimestampFormatter.format(ctx.clock.instant().atZone(ctx.clock.getZone))
      val backupName = s"${link.linkName}.dotbot-backup.$timestamp"
      val backupPath = ctx.paths.absFromExpanded(ctx.baseDirectory, backupName)
      if ctx.options.dryRun then
        ctx.log.action(s"Would backup ${link.linkName} to $backupName")
        BackupResult.BackedUp
      else
        if ctx.withFilesystem(ctx.fs.rename(link.linkPath, backupPath), _ => s"Failed to backup file ${link.linkName} to $backupName").isEmpty then
          BackupResult.Failed
        else
          ctx.log.action(s"Backed up file ${link.linkName} to $backupName")
          BackupResult.BackedUp
    else BackupResult.NotNeeded

  private def deleteLink(ctx: RuntimeContext, link: LinkResolution, options: LinkOptions): RemoveResult =
    sameFileConflict(ctx, link).getOrElse {
      shouldRemoveLink(ctx, link) match
        case Left(result)                  => result
        case Right(RemoveDecision.Keep)    => RemoveResult.Kept
        case Right(RemoveDecision.Remove) if ctx.options.dryRun =>
          ctx.log.action(s"Would remove ${link.linkName}")
          RemoveResult.Removed
        case Right(RemoveDecision.Remove)  => removeLink(ctx, link, options)
    }

  private def sameFileConflict(ctx: RuntimeContext, link: LinkResolution): Option[RemoveResult] =
    if ctx.fs.exists(link.linkPath) && !ctx.fs.isSymlink(link.linkPath) then
      ctx.withFilesystem(ctx.fs.sameFile(link.linkPath, link.absoluteTarget), _ => "Failed to compare file links") match
        case Some(true) =>
          ctx.log.warning(s"${link.linkName} appears to be the same file as ${link.absoluteTarget}.")
          Some(RemoveResult.FailedBeforeRemove)
        case Some(false) => None
        case None       => None
    else None

  private def shouldRemoveLink(ctx: RuntimeContext, link: LinkResolution): Either[RemoveResult, RemoveDecision] =
    if ctx.fs.isSymlink(link.linkPath) then
      ctx.withFilesystem(ctx.fs.readlink(link.linkPath), _ => s"Failed to inspect link ${link.linkName}") match
        case None              => Left(RemoveResult.FailedBeforeRemove)
        case Some(currentPath) => Right(removeWhen(currentPath != link.targetPath))
    else Right(removeWhen(ctx.fs.lexists(link.linkPath)))

  private def removeWhen(condition: Boolean): RemoveDecision =
    if condition then RemoveDecision.Remove else RemoveDecision.Keep

  /**
   * Remove whatever currently occupies the link path, so that the link can be created.
   *
   * A plain file or directory is only removed under `force`. `relink` on its own is about
   * replacing a symlink that points somewhere else, and must not delete real content.
   */
  private def removeLink(ctx: RuntimeContext, link: LinkResolution, options: LinkOptions): RemoveResult =
    val wasSymlink = ctx.fs.isSymlink(link.linkPath)
    if !wasSymlink && !options.force then RemoveResult.Kept
    else
      val failure = (_: Throwable) => s"Failed to remove ${link.linkName}"
      val removed =
        if wasSymlink then ctx.withFilesystem(ctx.fs.remove(link.linkPath), failure)
        else if ctx.fs.isDir(link.linkPath) then ctx.withFilesystem(ctx.fs.removeAll(link.linkPath), failure)
        else ctx.withFilesystem(ctx.fs.remove(link.linkPath), failure)

      if removed.isEmpty then RemoveResult.FailedAfterRemoveAttempt
      else
        ctx.log.action(s"Removing ${link.linkName}")
        RemoveResult.Removed

  private def createLink(
    ctx:           RuntimeContext,
    link:          LinkResolution,
    options:       LinkOptions,
    ignoreMissing: Boolean,
    assumeLinkPathAvailable: Boolean,
  ): Outcome =
    val linkExists = ctx.fs.lexists(link.linkPath)
    if (!linkExists || (ctx.options.dryRun && assumeLinkPathAvailable)) && (ignoreMissing || ctx.fs.exists(link.absoluteTarget)) then
      if ctx.options.dryRun then
        ctx.log.action(s"Would create ${options.linkType.label} ${link.cleanLinkName} -> ${link.targetPath}")
        Outcome.Ok
      else
        val result =
          if options.linkType == LinkType.Symlink then ctx.fs.symlink(link.targetPath, link.linkPath)
          else ctx.fs.hardlink(link.absoluteTarget, link.linkPath)
        if ctx.withFilesystem(result, _ => s"Linking failed ${link.cleanLinkName} -> ${link.targetPath}").isEmpty then Outcome.Failed
        else
            ctx.log.action(s"Creating ${options.linkType.label} ${link.cleanLinkName} -> ${link.targetPath}")
            Outcome.Ok
    else if ctx.fs.isSymlink(link.linkPath) then
      if options.linkType == LinkType.Symlink then
        ctx.withFilesystem(ctx.fs.readlink(link.linkPath), _ => s"Failed to inspect link ${link.cleanLinkName}") match
          case None =>
            Outcome.Failed
          case Some(current) if current == link.targetPath =>
            ctx.log.info(s"Link exists ${link.cleanLinkName} -> ${link.targetPath}")
            Outcome.Ok
          case Some(current) =>
            val term = if !ctx.fs.exists(link.linkPath) then "Invalid" else "Incorrect"
            ctx.log.warning(s"$term link ${link.cleanLinkName} -> $current")
            Outcome.Failed
      else
        ctx.log.warning(s"${link.cleanLinkName} already exists but is a symbolic link, not a hard link")
        Outcome.Failed
    else if options.linkType == LinkType.Hardlink && ctx.withFilesystem(ctx.fs.sameFile(link.linkPath, link.absoluteTarget), _ => s"Failed to compare file links for ${link.cleanLinkName}").contains(true) then
      ctx.log.info(s"Link exists ${link.cleanLinkName} -> ${link.targetPath}")
      Outcome.Ok
    else
      ctx.log.warning(s"${link.cleanLinkName} already exists but is a regular file or directory")
      Outcome.Failed
