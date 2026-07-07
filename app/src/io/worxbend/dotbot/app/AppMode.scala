package io.worxbend.dotbot.app

/**
 * Supported output renderers for plan mode.
 */
enum OutputFormat:
  case Text
  case Json

/**
 * Parser-level output representation before command mode resolution.
 */
object OutputFormat:
  def fromString(value: String): OutputFormatArgument =
    value match
      case "" | "text" => OutputFormatArgument.Valid(OutputFormat.Text)
      case "json"     => OutputFormatArgument.Valid(OutputFormat.Json)
      case other       => OutputFormatArgument.Invalid(other)

/**
 * Parsed plan output type from CLI input.
 */
enum OutputFormatArgument:
  case Valid(format: OutputFormat)
  case Invalid(value: String)

  /**
   * Convert a validated output argument into a concrete run mode.
   */
  def toRunMode: RunMode =
    this match
      case OutputFormatArgument.Valid(format) => RunMode.Plan(format)
      case OutputFormatArgument.Invalid(value) => RunMode.InvalidPlanOutput(value)

/**
 * Command mode selected from parsed CLI arguments.
 */
enum RunMode:
  case Apply
  case Validate
  case Plan(format: OutputFormat)
  case InvalidPlanOutput(value: String)
