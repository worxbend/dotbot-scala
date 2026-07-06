package io.worxbend.dotbot.core

import io.worxbend.dotbot.config.ConfigValue

object CreateHandler extends Handler:
  def canHandle(directive: String): Boolean = directive == "create"

  override def validate(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Unit] =
    data.asArray match
      case Some(paths) =>
        if paths.forall(_.asString.nonEmpty) then Right(())
        else Left("create directive item must be a string")
      case None =>
        data.asMap match
          case Some(map) =>
            val bad = map.collectFirst {
              case (path, value) if value != ConfigValue.NullValue && value.asMap.isEmpty =>
                s"create directive options for $path must be a map"
            }
            bad.toLeft(())
          case None => Left("create directive must be a list or map")

  override def plan(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Vector[Operation]] =
    validate(ctx, directive, data).map { _ =>
      data.asArray match
        case Some(paths) => paths.flatMap(_.asString).map(path => Operation(directive, path))
        case None =>
          val map = data.asMap.getOrElse(Map.empty)
          Values.sortedKeys(map).map(path => Operation(directive, path))
    }

  def handle(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Boolean] =
    val defaults = ctx.defaults.get("create").flatMap(_.asMap).getOrElse(Map.empty)
    val defaultMode = Values.modeValue(defaults.get("mode"), 0x1ff)

    data.asArray match
      case Some(paths) =>
        val success = paths.foldLeft(true) { (ok, item) =>
          item.asString.exists(createPath(ctx, _, defaultMode)) && ok
        }
        Right(finish(ctx, success, "All paths have been set up", "Some paths were not successfully set up"))
      case None =>
        data.asMap match
          case None => Left("create directive must be a list or map")
          case Some(map) =>
            val success = Values.sortedKeys(map).foldLeft(true) { (ok, path) =>
              val mode = map(path).asMap.map(local => Values.modeValue(local.get("mode"), defaultMode)).getOrElse(defaultMode)
              createPath(ctx, path, mode) && ok
            }
            Right(finish(ctx, success, "All paths have been set up", "Some paths were not successfully set up"))

  private def createPath(ctx: RuntimeContext, path: String, mode: Int): Boolean =
    val absolute = PathUtil.absFrom(ctx.baseDirectory, path)
    if ctx.fs.exists(absolute) then
      ctx.log.info(s"Path exists $absolute")
      true
    else if ctx.options.dryRun then
      ctx.log.action(s"Would create path $absolute")
      true
    else
      ctx.log.action(s"Creating path $absolute")
      ctx.fs.mkdirAll(absolute, mode) match
        case Left(error) =>
          ctx.log.warning(s"Failed to create path $absolute")
          ctx.log.debug(error.getMessage)
          false
        case Right(_) =>
          ctx.fs.chmod(absolute, mode) match
            case Left(error) =>
              ctx.log.warning(s"Failed to set mode for path $absolute")
              ctx.log.debug(error.getMessage)
              false
            case Right(_) => true

def finish(ctx: RuntimeContext, success: Boolean, okMessage: String, failMessage: String): Boolean =
  if success then ctx.log.info(okMessage) else ctx.log.error(failMessage)
  success
