package io.worxbend.dotbot.logging

import java.io.PrintStream

enum Level(val rank: Int):
  case Debug extends Level(0)
  case Info extends Level(1)
  case Action extends Level(2)
  case Warning extends Level(3)
  case Error extends Level(4)

final class Logger(out: PrintStream, private var minimum: Level = Level.Action):
  private var color = supportsColor(out)

  def setLevel(level: Level): Unit =
    minimum = level

  def useColor(enabled: Boolean): Unit =
    color = enabled

  def debug(message: String): Unit = log(Level.Debug, message)

  def info(message: String): Unit = log(Level.Info, message)

  def action(message: String): Unit = log(Level.Action, message)

  def warning(message: String): Unit = log(Level.Warning, message)

  def error(message: String): Unit = log(Level.Error, message)

  private def log(level: Level, message: String): Unit =
    if level.rank >= minimum.rank then
      out.println(s"${style(level)}${label(level)}${reset} ${messageStyle(level)}$message$reset")

  private def label(level: Level): String =
    level match
      case Level.Debug   => "debug"
      case Level.Info    => "info "
      case Level.Action  => "step "
      case Level.Warning => "warn "
      case Level.Error   => "error"

  private def style(level: Level): String =
    if !color then ""
    else
      level match
        case Level.Debug   => fansi.Color.Yellow.escape
        case Level.Info    => fansi.Color.Blue.escape
        case Level.Action  => fansi.Color.Green.escape
        case Level.Warning => fansi.Color.Yellow.escape
        case Level.Error   => fansi.Color.Red.escape

  private def messageStyle(level: Level): String =
    if color && level == Level.Error then fansi.Color.Red.escape else ""

  private def reset: String =
    if color then fansi.Attr.Reset.escape else ""

  private def supportsColor(out: PrintStream): Boolean =
    sys.env.get("NO_COLOR").isEmpty &&
      !sys.env.get("TERM").exists(_.equalsIgnoreCase("dumb")) &&
      System.console() != null
