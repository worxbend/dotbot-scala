package io.worxbend.dotbot.core

final case class Operation(
  directive: String,
  target:    String = "",
  detail:    String = "",
)

final case class Plan(operations: Vector[Operation] = Vector.empty)

object PlanOutput:
  def text(plan: Plan, taskCount: Int, configFileCount: Int, base: String): String =
    val builder = StringBuilder()
    builder.append(
      s"Plan: ${plan.operations.size} operation(s), $taskCount task(s), $configFileCount config file(s), base $base\n",
    )
    plan.operations.foreach { operation =>
      builder.append(f"${operation.directive}%-7s ${operation.target}")
      if operation.detail.nonEmpty then builder.append(detailSuffix(operation))
      builder.append('\n')
    }
    builder.result()

  def json(plan: Plan, taskCount: Int, configFileCount: Int, base: String): String =
    val doc = ujson.Obj(
      "task_count" -> taskCount,
      "config_file_count" -> configFileCount,
      "operation_count" -> plan.operations.size,
      "base" -> base,
      "operations" -> ujson.Arr.from(
        plan.operations.map { operation =>
          ujson.Obj(
            "directive" -> operation.directive,
            "target" -> operation.target,
            "detail" -> operation.detail,
          )
        },
      ),
    )
    ujson.write(doc, indent = 2) + "\n"

  private def detailSuffix(operation: Operation): String =
    operation.directive match
      case "link"  => s" -> ${operation.detail}"
      case "shell" => s" [${operation.detail}]"
      case _       => s" (${operation.detail})"
