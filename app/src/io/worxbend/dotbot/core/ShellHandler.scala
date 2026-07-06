package io.worxbend.dotbot.core

import io.worxbend.dotbot.config.ConfigValue
import io.worxbend.dotbot.shell.ShellOptions

object ShellHandler extends Handler:
  def canHandle(directive: String): Boolean = directive == "shell"

  override def validate(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Unit] =
    data.asArray match
      case Some(items) =>
        if items.forall(shellCommandSpec(_).isDefined) then Right(())
        else Left("shell directive item must include a command")
      case None => Left("shell directive must be a list")

  override def plan(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Vector[Operation]] =
    validate(ctx, directive, data).map { _ =>
      data.asArray.toVector.flatten.flatMap { item =>
        shellCommandSpec(item).map { case (command, description) => Operation(directive, command, description) }
      }
    }

  def handle(ctx: RuntimeContext, directive: String, data: ConfigValue): Either[String, Boolean] =
    val items = data.asArray.getOrElse(Vector.empty)
    if data.asArray.isEmpty then return Left("shell directive must be a list")

    val defaults = ctx.defaults.get("shell").flatMap(_.asMap).getOrElse(Map.empty)
    val success = items.foldLeft(true) { (ok, item) =>
      shellCommandSpec(item) match
        case None => false
        case Some((command, message)) =>
          val local = item.asMap.getOrElse(Map.empty)
          val quiet = Values.boolValue(local, "quiet", Values.boolValue(defaults, "quiet", false))
          val stdin = Values.boolValue(local, "stdin", Values.boolValue(defaults, "stdin", false))
          val stdout = ctx.options.verbose > 1 || Values.boolValue(local, "stdout", Values.boolValue(defaults, "stdout", false))
          val stderr = ctx.options.verbose > 1 || Values.boolValue(local, "stderr", Values.boolValue(defaults, "stderr", false))
          val prefix = if ctx.options.dryRun then "Would run command " else ""

          if quiet then
            if message.nonEmpty then ctx.log.info(prefix + message)
          else if message.isEmpty then ctx.log.action(prefix + command)
          else ctx.log.action(s"$prefix$message [$command]")

          val commandOk =
            if ctx.options.dryRun then true
            else
              val exitCode = ctx.shell.run(
                command,
                ShellOptions(
                  cwd = ctx.baseDirectory,
                  enableStdin = stdin,
                  enableStdout = stdout,
                  enableStderr = stderr,
                  timeout = ctx.options.shellTimeout,
                ),
              )
              if exitCode != 0 then
                ctx.log.warning(s"Command [$command] failed")
                false
              else true
          commandOk && ok
    }

    Right(finish(ctx, success, "All commands have been executed", "Some commands were not successfully executed"))

  private def shellCommandSpec(item: ConfigValue): Option[(String, String)] =
    item.asString.map(_ -> "").orElse {
      item.asMap.flatMap { map =>
        map.get("command").flatMap(_.asString).map { command =>
          command -> map.get("description").flatMap(_.asString).getOrElse("")
        }
      }
    }.orElse {
      item.asArray.flatMap { list =>
        list.headOption.flatMap(_.asString).map { command =>
          command -> list.drop(1).headOption.flatMap(_.asString).getOrElse("")
        }
      }
    }
