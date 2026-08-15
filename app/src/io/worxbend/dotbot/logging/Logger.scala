package io.worxbend.dotbot.logging

import io.worxbend.dotbot.core.Environment
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
 * What the destination terminal can render.
 *
 * @param color true when ANSI color escapes should be emitted.
 * @param symbols true when emoji badges and icons should be emitted.
 */
final case class TerminalCapabilities(color: Boolean, symbols: Boolean)

object TerminalCapabilities:
  /** Nothing decorative: the safe choice for pipes, files, and tests. */
  val plain: TerminalCapabilities = TerminalCapabilities(color = false, symbols = false)

  /**
   * Work out what the terminal supports from an [[Environment]] and whether a console is attached.
   *
   * Taking both as parameters is what makes this testable: the rules below are the interesting
   * part, and reading them out of the real JVM would mean mutating global state to exercise them.
   *
   * @param env environment to read `NO_COLOR`, `NO_EMOJI` and `TERM` from.
   * @param consoleAttached whether output is going to an interactive terminal.
   */
  def from(env: Environment, consoleAttached: Boolean): TerminalCapabilities =
    val dumbTerminal = env.variable("TERM").exists(_.equalsIgnoreCase("dumb"))
    val renderable = consoleAttached && !dumbTerminal
    TerminalCapabilities(
      color = renderable && env.variable("NO_COLOR").isEmpty,
      symbols = renderable && env.variable("NO_EMOJI").isEmpty,
    )

  /** Capabilities of the real process environment, for the composition root. */
  def detect: TerminalCapabilities =
    from(Environment.System, Option(System.console()).nonEmpty)

/**
 * Structured, leveled logger for CLI-facing output.
 *
 * @param out destination for informational output.
 * @param err destination for warnings and errors.
 * @param minimum minimum level to emit.
 * @param color enable ANSI colors for labels/messages.
 * @param stylishSymbols include icon badges for levels when enabled.
 */
final class Logger(
  out: PrintStream,
  err: PrintStream,
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
      val name = label(level)
      // The badge is styled, but the padding that aligns messages into a column is not: keeping
      // spaces outside the ANSI reset makes the plain and colored forms line up identically.
      val badge = s"${style(level)}${symbol(level)}$name${reset}"
      val padding = " " * (Logger.LabelWidth - name.length)
      streamFor(level).println(s"$badge$padding${messageStyle(level)}$message$reset")

  /**
   * Diagnostics go to the error stream so that they can be separated from real output.
   *
   * Without this, `dotbot plan --output json > plan.json` writes any warning into the middle of
   * the JSON document and the file no longer parses, with no way to filter it out.
   */
  private def streamFor(level: Level): PrintStream =
    level match
      case Level.Warning | Level.Error => err
      case _                           => out

  private def label(level: Level): String =
    level match
      case Level.Debug   => "debug"
      case Level.Info    => "info"
      case Level.Action  => "step"
      case Level.Warning => "warn"
      case Level.Error   => "error"

  private def symbol(level: Level): String =
    if !stylishSymbols then ""
    else
      level match
        case Level.Debug   => "🐞 "
        case Level.Info    => "🪄 "
        case Level.Action  => "🚀 "
        case Level.Warning => "⚠️  "
        case Level.Error   => "💥 "

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
   * Column width reserved for the level label, so messages start at the same offset on every line.
   * Locked by `LoggerSuite` as part of the output contract.
   */
  private[logging] val LabelWidth: Int = 6

  /**
   * Create a logger with default level (`Action`) and auto-detected
   * color + symbol capabilities.
   */
  def apply(out: PrintStream): Logger =
    Logger(out, Level.Action, TerminalCapabilities.detect)

  /**
   * Create a logger with an explicit minimum level and auto-detected
   * color + symbol capabilities.
   */
  def apply(out: PrintStream, minimum: Level): Logger =
    Logger(out, minimum, TerminalCapabilities.detect)

  /**
   * Create a logger with an explicit minimum level and terminal capabilities, writing everything
   * to a single stream.
   */
  def apply(out: PrintStream, minimum: Level, capabilities: TerminalCapabilities): Logger =
    new Logger(out, out, minimum, capabilities.color, capabilities.symbols)

  /**
   * Create a logger with an explicit minimum level and color preference.
   * Symbol rendering remains auto-detected.
   */
  def apply(out: PrintStream, minimum: Level, color: Boolean): Logger =
    new Logger(out, out, minimum, color, TerminalCapabilities.detect.symbols)

  /**
   * Create a logger with explicit level, color and symbol preferences.
   */
  def apply(out: PrintStream, minimum: Level, color: Boolean, symbols: Boolean): Logger =
    new Logger(out, out, minimum, color, symbols)

  /**
   * Create a logger that separates diagnostics from output: warnings and errors go to `err`,
   * everything else to `out`.
   */
  def split(out: PrintStream, err: PrintStream, minimum: Level, capabilities: TerminalCapabilities): Logger =
    new Logger(out, err, minimum, capabilities.color, capabilities.symbols)
