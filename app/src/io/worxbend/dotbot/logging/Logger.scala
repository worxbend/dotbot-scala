package io.worxbend.dotbot.logging

import io.worxbend.dotbot.core.Log

import java.io.PrintStream

/**
 * Logging severity levels used by the CLI and core runtime.
 */
enum Level(val rank: Int):
  case Debug extends Level(0)
  case Info extends Level(1)
  case Action extends Level(2)
  case Warning extends Level(3)
  case Error extends Level(4)

/**
 * Whether pretty symbolic output (emoji badges and icons) is enabled.
 *
 * @param enabled true when emoji output should be rendered.
 */
final case class SymbolSupport(enabled: Boolean)

/**
 * Detects whether symbolic output can be used in the current terminal.
 * This mirrors the environment guards used for color rendering.
 */
object SymbolSupport:
  def detect: SymbolSupport =
    SymbolSupport(
      sys.env.get("NO_EMOJI").isEmpty &&
        !sys.env.get("TERM").exists(_.equalsIgnoreCase("dumb")) &&
        Option(System.console()).nonEmpty,
    )

/**
 * Whether ANSI color rendering is enabled.
 *
 * @param enabled true when the terminal supports ANSI color and output is not disabled by env.
 */
final case class ColorSupport(enabled: Boolean)

/**
 * Detects whether ANSI color output is expected to render in the current environment.
 */
object ColorSupport:
  def detect: ColorSupport =
    ColorSupport(
      sys.env.get("NO_COLOR").isEmpty &&
        !sys.env.get("TERM").exists(_.equalsIgnoreCase("dumb")) &&
        Option(System.console()).nonEmpty,
    )

/**
 * Structured, leveled logger for CLI-facing output.
 *
 * @param out destination print stream.
 * @param minimum minimum level to emit.
 * @param color enable ANSI colors for labels/messages.
 * @param stylishSymbols include icon badges for levels when enabled.
 */
final class Logger(
  out: PrintStream,
  minimum: Level,
  color: Boolean,
  private[logging] val stylishSymbols: Boolean = false,
) extends Log:

  def debug(message: String): Unit = log(Level.Debug, message)

  def info(message: String): Unit = log(Level.Info, message)

  def action(message: String): Unit = log(Level.Action, message)

  def warning(message: String): Unit = log(Level.Warning, message)

  def error(message: String): Unit = log(Level.Error, message)

  private def log(level: Level, message: String): Unit =
    if level.rank >= minimum.rank then
      val badge = s"${style(level)}${symbol(level)}${label(level)}${reset}"
      out.println(s"$badge ${messageStyle(level)}$message$reset")

  private def label(level: Level): String =
    level match
      case Level.Debug   => "debug "
      case Level.Info    => "info  "
      case Level.Action  => "step  "
      case Level.Warning => "warn  "
      case Level.Error   => "error "

  private def symbol(level: Level): String =
    if stylishSymbols then
      level match
        case Level.Debug   => "🐞 "
        case Level.Info    => "🪄 "
        case Level.Action  => "🚀 "
        case Level.Warning => "⚠️  "
        case Level.Error   => "💥 "
    else "    "

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

object Logger:
  /**
   * Create a logger with default level (`Action`) and auto-detected
   * color + symbol capabilities.
   */
  def apply(out: PrintStream): Logger =
    Logger(out, Level.Action, ColorSupport.detect.enabled, SymbolSupport.detect.enabled)

  /**
   * Create a logger with an explicit minimum level and auto-detected
   * color + symbol capabilities.
   */
  def apply(out: PrintStream, minimum: Level): Logger =
    Logger(out, minimum, ColorSupport.detect.enabled, SymbolSupport.detect.enabled)

  /**
   * Create a logger with an explicit minimum level and color preference.
   * Symbol rendering remains auto-detected.
   */
  def apply(out: PrintStream, minimum: Level, color: Boolean): Logger =
    Logger(out, minimum, color, SymbolSupport.detect.enabled)

  /**
   * Create a logger with explicit level, color and symbol preferences.
   */
  def apply(out: PrintStream, minimum: Level, color: Boolean, symbols: Boolean): Logger =
    new Logger(out, minimum, color, symbols)
