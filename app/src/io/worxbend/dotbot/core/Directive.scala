package io.worxbend.dotbot.core

enum Directive(val label: String):
  case Defaults extends Directive("defaults")
  case Clean extends Directive("clean")
  case Create extends Directive("create")
  case Link extends Directive("link")
  case Shell extends Directive("shell")
  case Unknown(raw: String) extends Directive(raw)

object Directive:
  /**
   * The directives a config may name, by their label.
   *
   * Each label comes from the enum case itself rather than being written out a second time, so a
   * renamed directive cannot fall out of step with the name a config may use. `Unknown` is absent
   * on purpose: it represents a name that was *not* recognized.
   */
  private val known: Vector[Directive] =
    Vector(Directive.Defaults, Directive.Clean, Directive.Create, Directive.Link, Directive.Shell)

  private val byLabel: Map[String, Directive] =
    known.map(directive => directive.label -> directive).toMap

  def fromString(value: String): Option[Directive] =
    byLabel.get(value)

  def parse(value: String): Directive =
    fromString(value).getOrElse(Directive.Unknown(value))
