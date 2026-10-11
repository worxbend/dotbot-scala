package io.worxbend.dotbot.core

final case class Operation(
    directive: Directive,
    target: String = "",
    detail: String = "",
    detailStyle: DetailStyle = DetailStyle.Parenthesized,
)

enum DetailStyle:
  case Parenthesized
  case LinkTarget
  case ShellDescription

  def suffix(detail: String): String =
    this match
      case DetailStyle.Parenthesized    => s" ($detail)"
      case DetailStyle.LinkTarget       => s" -> $detail"
      case DetailStyle.ShellDescription => s" [$detail]"

final case class Plan(operations: Vector[Operation] = Vector.empty)
