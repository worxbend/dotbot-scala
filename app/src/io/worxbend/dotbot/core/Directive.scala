package io.worxbend.dotbot.core

enum Directive(val label: String):
  case Defaults extends Directive("defaults")
  case Clean extends Directive("clean")
  case Create extends Directive("create")
  case Link extends Directive("link")
  case Shell extends Directive("shell")
  case Unknown(raw: String) extends Directive(raw)

object Directive:
  def fromString(value: String): Option[Directive] =
    value match
      case "defaults" => Some(Directive.Defaults)
      case "clean"    => Some(Directive.Clean)
      case "create"   => Some(Directive.Create)
      case "link"     => Some(Directive.Link)
      case "shell"    => Some(Directive.Shell)
      case _          => None

  def parse(value: String): Directive =
    fromString(value).getOrElse(Directive.Unknown(value))
