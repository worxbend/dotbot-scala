package io.worxbend.dotbot.logging

/** Render config text as visible data; never interpret it as terminal commands or forged log lines. */
object TerminalText:
  def escape(text: String): String =
    if !text.exists(Character.isISOControl) then text
    else
      text.flatMap {
        case '\n'                             => "\\n"
        case '\r'                             => "\\r"
        case '\t'                             => "\\t"
        case ch if Character.isISOControl(ch) => f"\\u${ch.toInt}%04x"
        case ch                               => ch.toString
      }
