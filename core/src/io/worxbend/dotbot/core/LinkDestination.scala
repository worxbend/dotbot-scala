package io.worxbend.dotbot.core

import java.time.format.DateTimeFormatter

final private[core] case class LinkResolution(
    target: String,
    linkName: String,
    linkPath: String,
    absoluteTarget: String,
    targetPath: String,
):
  def cleanLinkName: String = PathUtil.clean(linkName)

private[core] enum DestinationPreparation:
  case Unchanged, Available, Failed

/** Prepares a destination in order. A failed prerequisite prevents every later mutation. */
final private[core] class LinkDestination(ctx: RuntimeContext, link: LinkResolution):
  import DestinationPreparation.*

  def prepare(options: LinkOptions): DestinationPreparation =
    if replacementWouldDamageSource(options) then Failed
    else if options.create && !createParent().successful then Failed
    else if options.backup && ctx.fs.exists(link.linkPath) && !ctx.fs.isSymlink(link.linkPath) then backup()
    else if options.force || options.relink then remove(options)
    else Unchanged

  private def replacementWouldDamageSource(options: LinkOptions): Boolean =
    val replacing = options.backup || options.force || options.relink
    if !replacing || !ctx.fs.exists(link.linkPath) || ctx.fs.isSymlink(link.linkPath) || !ctx.fs.exists(
        link.absoluteTarget,
      )
    then false
    else
      ctx.withFilesystem(ctx.fs.sameFile(link.linkPath, link.absoluteTarget), _ => "Failed to compare file links") match
        case None        => true
        case Some(true)  =>
          ctx.log.warning(s"${link.linkName} appears to be the same file as ${link.absoluteTarget}.")
          true
        case Some(false) => ctx.fs.isDir(link.linkPath) && containsSource()

  private def containsSource(): Boolean =
    val resolved = for
      destination <- ctx.withFilesystem(ctx.fs.realpath(link.linkPath), _ => s"Failed to inspect ${link.linkName}")
      source      <- ctx.withFilesystem(ctx.fs.realpath(link.absoluteTarget), _ => s"Failed to inspect ${link.target}")
    yield PathUtil.contains(destination, source)
    resolved match
      case None        => true
      case Some(true)  =>
        ctx.log.warning(s"Refusing to replace ${link.linkName}: it contains source ${link.target}")
        true
      case Some(false) => false

  private def createParent(): Outcome =
    val parent = PathUtil.dirname(link.linkPath)
    if ctx.fs.isDir(parent) then Outcome.Ok
    else if ctx.fs.lexists(parent) then
      ctx.log.warning(s"Cannot create directory $parent: path already exists")
      Outcome.Failed
    else if ctx.options.dryRun then
      ctx.log.action(s"Would create directory $parent")
      Outcome.Ok
    else if ctx.withFilesystem(ctx.fs.mkdirAll(parent), _ => s"Failed to create directory $parent").isEmpty then
      Outcome.Failed
    else
      ctx.log.action(s"Creating directory $parent")
      Outcome.Ok

  private def backup(): DestinationPreparation =
    val timestamp  = LinkDestination.BackupTimestampFormatter.format(ctx.clock.instant().atZone(ctx.clock.getZone))
    val backupName = s"${link.linkName}.dotbot-backup.$timestamp"
    val backupPath = ctx.paths.absFromExpanded(ctx.baseDirectory, backupName)
    if ctx.fs.lexists(backupPath) then
      ctx.log.warning(s"Failed to backup file ${link.linkName} to $backupName: destination already exists")
      Failed
    else if ctx.options.dryRun then
      ctx.log.action(s"Would backup ${link.linkName} to $backupName")
      Available
    else if ctx
        .withFilesystem(
          ctx.fs.rename(link.linkPath, backupPath),
          _ => s"Failed to backup file ${link.linkName} to $backupName",
        )
        .isEmpty
    then Failed
    else
      ctx.log.action(s"Backed up file ${link.linkName} to $backupName")
      Available

  private def remove(options: LinkOptions): DestinationPreparation =
    removalNeeded(options) match
      case None                             => Failed
      case Some(false)                      => Unchanged
      case Some(true) if ctx.options.dryRun =>
        ctx.log.action(s"Would remove ${link.linkName}")
        Available
      case Some(true)                       =>
        val result =
          if ctx.fs.isSymlink(link.linkPath) || !ctx.fs.isDir(link.linkPath) then ctx.fs.remove(link.linkPath)
          else ctx.fs.removeAll(link.linkPath)
        if ctx.withFilesystem(result, _ => s"Failed to remove ${link.linkName}").isEmpty then Failed
        else
          ctx.log.action(s"Removing ${link.linkName}")
          Available

  /** The same eligibility rule is used before dry-run reporting and real removal. */
  private def removalNeeded(options: LinkOptions): Option[Boolean] =
    if ctx.fs.isSymlink(link.linkPath) then
      ctx
        .withFilesystem(ctx.fs.readlink(link.linkPath), _ => s"Failed to inspect link ${link.linkName}")
        .map(current => options.linkType != LinkType.Symlink || current != link.targetPath)
    else Some(options.force && ctx.fs.lexists(link.linkPath))

private[core] object LinkDestination:
  private val BackupTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
