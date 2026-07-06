package io.worxbend.dotbot.core

import io.worxbend.dotbot.config.ConfigValue
import io.worxbend.dotbot.shell.ShellOptions

import java.nio.file.Paths
import java.time.format.DateTimeFormatter

object LinkHandler extends Handler:
  def canHandle(directive: String): Boolean = directive == "link"

  override def validate(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Unit] =
    data.asMap match
      case None => Left("link directive must be a map")
      case Some(links) =>
        val defaults = ctx.defaults.get("link").flatMap(_.asMap).getOrElse(Map.empty)
        val defaultOptions = LinkOptions.fromDefaults(defaults)
        if !LinkOptions.validLinkType(defaultOptions.linkType) then
          Left(s"default link type is not recognized: ${defaultOptions.linkType}")
        else
          Values.sortedKeys(links).foldLeft(Right(()): Either[String, Unit]) { (acc, linkName) =>
            acc.flatMap { _ =>
              links(linkName).asMap match
                case Some(targetMap) =>
                  validateLinkMap(linkName, targetMap).flatMap { _ =>
                    val options = LinkOptions.merge(defaultOptions, targetMap)
                    if LinkOptions.validLinkType(options.linkType) then Right(())
                    else Left(s"link type is not recognized: ${options.linkType}")
                  }
                case None =>
                  if links(linkName) == ConfigValue.NullValue || links(linkName).asString.nonEmpty then Right(())
                  else Left(s"link target for $linkName must be a string or map")
            }
          }

  override def plan(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Vector[Operation]] =
    validate(ctx, directive, data).map { _ =>
      val links = data.asMap.getOrElse(Map.empty)
      Values.sortedKeys(links).map { linkName =>
        val target = links(linkName)
        val detail = target.asMap match
          case Some(map) => Values.defaultTarget(linkName, map.getOrElse("path", ConfigValue.NullValue))
          case None      => Values.defaultTarget(linkName, target)
        Operation(directive, linkName, detail)
      }
    }

  def handle(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Boolean] =
    data.asMap match
      case None => Left("link directive must be a map")
      case Some(links) =>
        val defaults = ctx.defaults.get("link").flatMap(_.asMap).getOrElse(Map.empty)
        val defaultOptions = LinkOptions.fromDefaults(defaults)
        if !LinkOptions.validLinkType(defaultOptions.linkType) then
          ctx.log.warning(s"The default link type is not recognized: '${defaultOptions.linkType}'")
          Right(false)
        else
          val success = Values.sortedKeys(links).foldLeft(true) { (ok, rawLinkName) =>
            handleOneLink(ctx, rawLinkName, links(rawLinkName), defaultOptions) && ok
          }
          Right(finish(ctx, success, "All links have been set up", "Some links were not successfully set up"))

  private def validateLinkMap(linkName: String, values: Map[String, ConfigValue]): Either[String, Unit] =
    if values.get("path").exists(value => value != ConfigValue.NullValue && value.asString.isEmpty) then
      Left(s"link path for $linkName must be a string")
    else if values.get("type").exists(_.asString.isEmpty) then Left(s"link type for $linkName must be a string")
    else if values.get("exclude").exists(value => !Values.isStringList(value)) then
      Left(s"link exclude for $linkName must be a list of strings")
    else Right(())

  private def handleOneLink(
    ctx:            RuntimeContext,
    rawLinkName:    String,
    target:         ConfigValue,
    defaultOptions: LinkOptions,
  ): Boolean =
    var options = defaultOptions
    val linkName = PathUtil.path(rawLinkName)
    val rawPath =
      target.asMap match
        case Some(targetMap) =>
          options = LinkOptions.merge(options, targetMap)
          if !LinkOptions.validLinkType(options.linkType) then
            ctx.log.warning(s"The link type is not recognized: '${options.linkType}'")
            return false
          Values.defaultTarget(linkName, targetMap.getOrElse("path", ConfigValue.NullValue))
        case None =>
          Values.defaultTarget(linkName, target)

    val sourcePath = PathUtil.clean(PathUtil.path(rawPath))
    val conditionalOk =
      options.ifCommand.isEmpty ||
        ctx.shell.run(options.ifCommand, ShellOptions(cwd = ctx.baseDirectory, timeout = ctx.options.shellTimeout)) == 0

    if !conditionalOk then
      ctx.log.info(s"Skipping $linkName")
      true
    else if options.glob && Glob.hasGlobChars(sourcePath) then
      val pattern = PathUtil.absFrom(ctx.baseDirectory, sourcePath)
      val excludes = options.exclude.map(item => PathUtil.absFrom(ctx.baseDirectory, PathUtil.path(item)))
      Glob.createGlobResults(pattern, excludes) match
        case Left(error) =>
          ctx.log.warning(s"Unable to expand glob '$sourcePath': $error")
          false
        case Right(matches) =>
          ctx.log.debug(s"Globs from '$sourcePath': ${pprint.apply(matches)}")
          matches.foldLeft(true) { (globOk, fullItem) =>
            val itemName =
              val raw = Glob.globLinkItem(pattern, fullItem)
              if options.prefix.nonEmpty then options.prefix + raw else raw
            val globLinkName = PathUtil.join(linkName, itemName)
            processOneLink(ctx, fullItem, globLinkName, options, globbed = true) && globOk
          }
    else processOneLink(ctx, sourcePath, linkName, options, globbed = false)

  private def processOneLink(
    ctx:      RuntimeContext,
    target:   String,
    linkName: String,
    options:  LinkOptions,
    globbed:  Boolean,
  ): Boolean =
    var success = true
    val link = resolveLink(ctx, target, linkName, options)
    if options.create then success = createParent(ctx, link.linkPath) && success
    if !globbed && !options.ignoreMissing && !ctx.fs.exists(link.absoluteTarget) then
      ctx.log.warning(s"Nonexistent target ${link.linkName} -> ${link.target}")
      false
    else
      var didBackup = false
      var didDelete = false
      var backupSuccess = true
      if options.backup then
        val backupResult = backup(ctx, link)
        didBackup = backupResult._1
        backupSuccess = backupResult._2
        success = backupSuccess && success
      if (options.force || options.relink) && !(didBackup && backupSuccess) then
        val deleteResult = deleteLink(ctx, link, options)
        didDelete = deleteResult._1
        success = deleteResult._2 && success
      createLink(ctx, link, options, options.ignoreMissing, didBackup || didDelete) && success

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
    val absoluteTarget = PathUtil.absFrom(base, target)
    val linkPath = PathUtil.absFrom(ctx.baseDirectory, linkName)
    val targetPath =
      if options.relative then PathUtil.relative(PathUtil.dirname(linkPath), absoluteTarget)
      else absoluteTarget
    LinkResolution(target, linkName, linkPath, absoluteTarget, targetPath)

  private def baseDir(ctx: RuntimeContext, canonical: Boolean): String =
    if !canonical then ctx.baseDirectory else ctx.fs.realpath(ctx.baseDirectory).getOrElse(ctx.baseDirectory)

  private def createParent(ctx: RuntimeContext, linkPath: String): Boolean =
    val parent = PathUtil.dirname(linkPath)
    if ctx.fs.exists(parent) then true
    else if ctx.options.dryRun then
      ctx.log.action(s"Would create directory $parent")
      true
    else
      ctx.fs.mkdirAll(parent, 0x1ff) match
        case Left(error) =>
          ctx.log.warning(s"Failed to create directory $parent")
          ctx.log.debug(error.getMessage)
          false
        case Right(_) =>
          ctx.log.action(s"Creating directory $parent")
          true

  private def backup(ctx: RuntimeContext, link: LinkResolution): (Boolean, Boolean) =
    if ctx.fs.exists(link.linkPath) && !ctx.fs.isSymlink(link.linkPath) then
      val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ctx.clock.instant().atZone(ctx.clock.getZone))
      val backupName = s"${link.linkName}.dotbot-backup.$timestamp"
      val backupPath = PathUtil.absFrom(ctx.baseDirectory, backupName)
      if ctx.options.dryRun then
        ctx.log.action(s"Would backup ${link.linkName} to $backupName")
        true -> true
      else
        ctx.fs.rename(link.linkPath, backupPath) match
          case Left(error) =>
            ctx.log.warning(s"Failed to backup file ${link.linkName} to $backupName")
            ctx.log.debug(error.getMessage)
            false -> false
          case Right(_) =>
            ctx.log.action(s"Backed up file ${link.linkName} to $backupName")
            true -> true
    else false -> true

  private def deleteLink(ctx: RuntimeContext, link: LinkResolution, options: LinkOptions): (Boolean, Boolean) =
    if ctx.fs.exists(link.linkPath) && !ctx.fs.isSymlink(link.linkPath) then
      ctx.fs.sameFile(link.linkPath, link.absoluteTarget) match
        case Right(true) =>
          ctx.log.warning(s"${link.linkName} appears to be the same file as ${link.absoluteTarget}.")
          return false -> false
        case _ =>

    val shouldRemove =
      if ctx.fs.isSymlink(link.linkPath) then
        ctx.fs.readlink(link.linkPath) match
          case Left(error) =>
            ctx.log.warning(s"Failed to inspect link ${link.linkName}")
            ctx.log.debug(error.getMessage)
            return false -> false
          case Right(current) => current != link.targetPath
      else ctx.fs.lexists(link.linkPath)

    if !shouldRemove then false -> true
    else if ctx.options.dryRun then
      ctx.log.action(s"Would remove ${link.linkName}")
      true -> true
    else
      val result =
        if ctx.fs.isSymlink(link.linkPath) then Some(ctx.fs.remove(link.linkPath))
        else if options.force && ctx.fs.isDir(link.linkPath) then Some(ctx.fs.removeAll(link.linkPath))
        else if options.force then Some(ctx.fs.remove(link.linkPath))
        else None

      result match
        case None => false -> true
        case Some(Left(error)) =>
          ctx.log.warning(s"Failed to remove ${link.linkName}")
          ctx.log.debug(error.getMessage)
          true -> false
        case Some(Right(_)) =>
          ctx.log.action(s"Removing ${link.linkName}")
          true -> true

  private def createLink(
    ctx:           RuntimeContext,
    link:          LinkResolution,
    options:       LinkOptions,
    ignoreMissing: Boolean,
    assumeGone:    Boolean,
  ): Boolean =
    val linkExists = ctx.fs.lexists(link.linkPath)
    if (!linkExists || (ctx.options.dryRun && assumeGone)) && (ignoreMissing || ctx.fs.exists(link.absoluteTarget)) then
      if ctx.options.dryRun then
        ctx.log.action(s"Would create ${options.linkType} ${link.cleanLinkName} -> ${link.targetPath}")
        true
      else
        val result =
          if options.linkType == "symlink" then ctx.fs.symlink(link.targetPath, link.linkPath)
          else ctx.fs.hardlink(link.absoluteTarget, link.linkPath)
        result match
          case Left(error) =>
            ctx.log.warning(s"Linking failed ${link.cleanLinkName} -> ${link.targetPath}")
            ctx.log.debug(error.getMessage)
            false
          case Right(_) =>
            ctx.log.action(s"Creating ${options.linkType} ${link.cleanLinkName} -> ${link.targetPath}")
            true
    else if ctx.fs.isSymlink(link.linkPath) then
      if options.linkType == "symlink" then
        ctx.fs.readlink(link.linkPath) match
          case Left(error) =>
            ctx.log.warning(s"Failed to inspect link ${link.cleanLinkName}")
            ctx.log.debug(error.getMessage)
            false
          case Right(current) if current == link.targetPath =>
            ctx.log.info(s"Link exists ${link.cleanLinkName} -> ${link.targetPath}")
            true
          case Right(current) =>
            val term = if !ctx.fs.exists(link.linkPath) then "Invalid" else "Incorrect"
            ctx.log.warning(s"$term link ${link.cleanLinkName} -> $current")
            false
      else
        ctx.log.warning(s"${link.cleanLinkName} already exists but is a symbolic link, not a hard link")
        false
    else if options.linkType == "hardlink" && ctx.fs.sameFile(link.linkPath, link.absoluteTarget).contains(true) then
      ctx.log.info(s"Link exists ${link.cleanLinkName} -> ${link.targetPath}")
      true
    else
      ctx.log.warning(s"${link.cleanLinkName} already exists but is a regular file or directory")
      false
