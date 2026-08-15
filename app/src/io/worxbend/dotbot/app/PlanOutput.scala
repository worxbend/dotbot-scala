package io.worxbend.dotbot.app

import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.Plan
import io.worxbend.dotbot.core.Operation

/**
 * Renderers for `plan` command output.
 */
object PlanOutput:
  /**
   * Render a human-readable plan as text.
   *
   * @param stylish when true, render emoji icons and a decorated header.
   */
  def text(plan: Plan, taskCount: Int, configFileCount: Int, base: String, stylish: Boolean = false): String =
    val lines = if stylish then stylishHeader(plan.operations.size, taskCount, configFileCount, base) else oldHeader(plan, taskCount, configFileCount, base)
    val operations = plan.operations.map(operation => renderTextOperation(operation, stylish))
    (lines ++ operations).mkString("", "\n", "\n")

  /**
   * Render a machine-readable JSON plan document.
   */
  def json(plan: Plan, taskCount: Int, configFileCount: Int, base: String): String =
    val doc = ujson.Obj(
      "task_count" -> taskCount,
      "config_file_count" -> configFileCount,
      "operation_count" -> plan.operations.size,
      "base" -> base,
      "operations" -> ujson.Arr.from(
        plan.operations.map { operation =>
          ujson.Obj(
            "directive" -> operation.directive.label,
            "target" -> operation.target,
            "detail" -> operation.detail,
          )
        },
      ),
    )
    ujson.write(doc, indent = 2) + "\n"

  private def oldHeader(plan: Plan, taskCount: Int, configFileCount: Int, base: String): Vector[String] =
    Vector(s"Plan: ${plan.operations.size} operation(s), $taskCount task(s), $configFileCount config file(s), base $base")

  private def stylishHeader(operationCount: Int, taskCount: Int, configFileCount: Int, base: String): Vector[String] =
    val metricLines = Vector(
      "🗺️  plan",
      s"🧭 operations: $operationCount operation(s)",
      s"🧩 tasks: $taskCount task(s)",
      s"📁 configs: $configFileCount config file(s)",
      s"🏠 base: $base",
    )
    val contentWidth = metricLines.map(displayWidth).max + 2
    val top = s"╭${"─" * (contentWidth + 2)}╮"
    val bottom = s"╰${"─" * (contentWidth + 2)}╯"
    val body = metricLines.map(line => s"│ $line${" " * (contentWidth - displayWidth(line))} │")
    top +: body :+ bottom

  /**
   * How many terminal columns a string occupies.
   *
   * `String.length` counts UTF-16 code units, so an emoji such as 🗺️ counts as three (a surrogate
   * pair plus a variation selector) while a terminal draws it in two columns. Padding the box with
   * that count left the right-hand border ragged. This is an approximation — terminals do not
   * agree on emoji width — but it is the common case and far closer than counting code units.
   */
  private def displayWidth(text: String): Int =
    text.codePoints().toArray.toVector.map(columnsFor).sum

  private def columnsFor(codePoint: Int): Int =
    if codePoint == VariationSelector || codePoint == ZeroWidthJoiner then 0
    else if codePoint >= FirstWideCodePoint then 2
    else 1

  private val VariationSelector: Int = 0xfe0f
  private val ZeroWidthJoiner: Int = 0x200d
  private val FirstWideCodePoint: Int = 0x1f300

  private def renderTextOperation(operation: Operation, stylish: Boolean): String =
    val detail =
      if operation.detail.isEmpty then ""
      else operation.detailStyle.suffix(operation.detail)
    if stylish then
      s"${directiveIcon(operation.directive)} ${operation.directive.label}  ${operation.target}$detail"
    else
      // Pad the directive label into a fixed column so targets line up. This must not be an `s`
      // interpolator: `%-7s` is a printf directive and `s` would emit it as literal text.
      s"${directiveColumn(operation.directive.label)}${operation.target}$detail"

  /**
   * Width of the directive column in plain text plan output, locked by `PlanOutputSuite`. An
   * unusually long label (an unknown directive echoes its raw name) still gets one separating
   * space rather than running into the target.
   */
  private val DirectiveColumnWidth: Int = 8

  private def directiveColumn(label: String): String =
    if label.length >= DirectiveColumnWidth then label + " "
    else label.padTo(DirectiveColumnWidth, ' ')

  private def directiveIcon(directive: Directive): String =
    directive match
      case Directive.Clean  => "🧹"
      case Directive.Create => "📁"
      case Directive.Link   => "🔗"
      case Directive.Shell  => "🛠️"
      case Directive.Defaults => "⚙️"
      case _                => "📌"
