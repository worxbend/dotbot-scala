package io.worxbend.dotbot.core

import io.worxbend.dotbot.config.ConfigValue

import java.nio.file.Paths

object CleanHandler extends Handler:
  def canHandle(directive: String): Boolean = directive == "clean"

  override def validate(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Unit] =
    data.asMap match
      case Some(map) =>
        val bad = map.collectFirst {
          case (target, value) if value != ConfigValue.NullValue && value.asMap.isEmpty =>
            s"clean directive options for $target must be a map"
        }
        bad.toLeft(())
      case None =>
        data.asArray match
          case Some(items) =>
            if items.forall(_.asString.nonEmpty) then Right(())
            else Left("clean directive item must be a string")
          case None => Left("clean directive must be a list or map")

  override def plan(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Vector[Operation]] =
    validate(ctx, directive, data).map { _ =>
      data.asMap match
        case Some(map) => Values.sortedKeys(map).map(Operation(directive, _))
        case None      => data.asArray.toVector.flatten.flatMap(_.asString).map(Operation(directive, _))
    }

  def handle(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Boolean] =
    val defaults = ctx.defaults.get("clean").flatMap(_.asMap).getOrElse(Map.empty)
    val defaultForce = Values.boolValue(defaults, "force", false)
    val defaultRecursive = Values.boolValue(defaults, "recursive", false)

    data.asMap match
      case Some(map) =>
        val success = Values.sortedKeys(map).foldLeft(true) { (ok, target) =>
          val local = map(target).asMap.getOrElse(Map.empty)
          val force = Values.boolValue(local, "force", defaultForce)
          val recursive = Values.boolValue(local, "recursive", defaultRecursive)
          cleanTarget(ctx, target, force, recursive) && ok
        }
        Right(finish(ctx, success, "All targets have been cleaned", "Some targets were not successfully cleaned"))
      case None =>
        data.asArray match
          case None => Left("clean directive must be a list or map")
          case Some(items) =>
            val success = items.foldLeft(true) { (ok, item) =>
              item.asString.exists(cleanTarget(ctx, _, defaultForce, defaultRecursive)) && ok
            }
            Right(finish(ctx, success, "All targets have been cleaned", "Some targets were not successfully cleaned"))

  private def cleanTarget(ctx: RuntimeContext, target: String, force: Boolean, recursive: Boolean): Boolean =
    val dir = PathUtil.absFrom(ctx.baseDirectory, target)
    if !ctx.fs.isDir(dir) then
      ctx.log.debug(s"Ignoring nonexistent directory $target")
      true
    else
      ctx.fs.listDir(dir) match
        case Left(error) =>
          ctx.log.warning(s"Failed to list directory $dir")
          ctx.log.debug(error.getMessage)
          false
        case Right(names) =>
          names.foldLeft(true) { (ok, name) =>
            val path = PathUtil.join(dir, name)
            val recursiveOk =
              if recursive && ctx.fs.isDir(path) && !ctx.fs.isSymlink(path) then cleanTarget(ctx, path, force, recursive)
              else true

            val removeOk =
              if !ctx.fs.exists(path) && ctx.fs.isSymlink(path) then removeBrokenLink(ctx, path, force)
              else true

            recursiveOk && removeOk && ok
          }

  private def removeBrokenLink(ctx: RuntimeContext, path: String, force: Boolean): Boolean =
    ctx.fs.readlink(path) match
      case Left(_) => false
      case Right(targetPath) =>
        val pointsAt = linkTargetPath(path, targetPath)
        if inDirectory(pointsAt, ctx.baseDirectory) || force then
          if ctx.options.dryRun then
            ctx.log.action(s"Would remove invalid link $path -> $pointsAt")
            true
          else
            ctx.log.action(s"Removing invalid link $path -> $pointsAt")
            ctx.fs.remove(path) match
              case Right(_) => true
              case Left(error) =>
                ctx.log.warning(s"Failed to remove invalid link $path")
                ctx.log.debug(error.getMessage)
                false
        else
          ctx.log.info(s"Link $path -> $pointsAt not removed.")
          true

  private def inDirectory(path: String, directory: String): Boolean =
    val dir = Paths.get(directory).normalize().toString
    val p = Paths.get(path).normalize().toString
    p == dir || p.startsWith(dir + "/")

  private def linkTargetPath(linkPath: String, targetPath: String): String =
    val target = Paths.get(targetPath)
    if target.isAbsolute then target.normalize().toString
    else Paths.get(PathUtil.dirname(linkPath), targetPath).normalize().toString
