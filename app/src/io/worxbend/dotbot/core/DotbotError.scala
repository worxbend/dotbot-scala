package io.worxbend.dotbot.core

sealed trait DotbotError:
  def render: String

object DotbotError:
  final case class Message(message: String) extends DotbotError:
    def render: String = message

  final case class Decode(message: String) extends DotbotError:
    def render: String = message

  final case class Validation(directive: Directive, cause: DotbotError) extends DotbotError:
    def render: String = renderActionContext("validating", directive, cause)

  final case class Execution(directive: Directive, cause: DotbotError) extends DotbotError:
    def render: String = renderActionContext("executing", directive, cause)

  final case class ActionNotHandled(directive: Directive) extends DotbotError:
    def render: String = s"action ${directive.label} not handled"

  final case class ConfigReadFailed(path: String, detail: String) extends DotbotError:
    def render: String = s"could not read config file \"$path\": $detail"

  /**
   * A config file was read but its text could not be parsed.
   *
   * The path is part of the message because `-c` accepts several config files, and a parser's own
   * message says only what is wrong, never which file it was reading.
   */
  final case class ConfigParseFailed(path: String, detail: String) extends DotbotError:
    def render: String = s"could not parse config file \"$path\": $detail"

  final case class UnsupportedConfigFormat(extension: String) extends DotbotError:
    def render: String = s"unsupported config file format .$extension"

  final case class ConfigRootNotTaskList(path: String) extends DotbotError:
    def render: String = s"configuration file \"$path\" must be a list of tasks"

  final case class ConfigTaskNotMapping(path: String, index: Int) extends DotbotError:
    def render: String = s"configuration task $index in \"$path\" must be a mapping"

  /**
   * Several directives share one line inside a single task, in a format that records only the line
   * a value came from. Their order cannot be recovered, and order changes what a config does.
   */
  final case class AmbiguousTaskOrder(path: String, line: Int, directives: Vector[String]) extends DotbotError:
    def render: String =
      s"configuration task in \"$path\" line $line writes ${directives.mkString(", ")} on one line, " +
        "so their order cannot be determined; put each directive on its own line"

  private def renderActionContext(phase: String, directive: Directive, cause: DotbotError): String =
    s"$phase action ${directive.label}: ${cause.render}"
