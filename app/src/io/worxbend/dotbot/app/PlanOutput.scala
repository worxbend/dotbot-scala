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
    val contentWidth = metricLines.map(_.length).max + 2
    val top = s"╭${"─" * (contentWidth + 2)}╮"
    val bottom = s"╰${"─" * (contentWidth + 2)}╯"
    val body = metricLines.map(line => s"│ ${line.padTo(contentWidth, ' ')} │")
    top +: body :+ bottom

  private def renderTextOperation(operation: Operation, stylish: Boolean): String =
    val detail =
      if operation.detail.isEmpty then ""
      else operation.detailStyle.suffix(operation.detail)
    if stylish then
      s"${directiveIcon(operation.directive)} ${operation.directive.label}  ${operation.target}$detail"
    else
      s"${operation.directive.label}%-7s ${operation.target}$detail"

  private def directiveIcon(directive: Directive): String =
    directive match
      case Directive.Clean  => "🧹"
      case Directive.Create => "📁"
      case Directive.Link   => "🔗"
      case Directive.Shell  => "🛠️"
      case Directive.Defaults => "⚙️"
      case _                => "📌"
