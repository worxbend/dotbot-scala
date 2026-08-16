package io.worxbend.dotbot.core

sealed trait DirectiveSpec:
  def directive: Directive

enum DecodeMode:
  case Validate, Execute

  def strict: Boolean =
    this == DecodeMode.Validate

final case class DirectiveDefaults(values: Map[Directive, ConfigValue]):
  def section(directive: Directive): Map[String, ConfigValue] =
    values.get(directive).flatMap(_.asMap).getOrElse(Map.empty)

object DirectiveDefaults:
  val empty: DirectiveDefaults = DirectiveDefaults(Map.empty)

  def from(value: ConfigValue): DirectiveDefaults =
    DirectiveDefaults(
      value.asMap
        .getOrElse(Map.empty)
        .flatMap { case (name, section) =>
          Directive.fromString(name).map(_ -> section)
        },
    )

enum CreateEntry:
  case Path(path: String, mode: FileMode)

  /** An entry that could not be understood. `description` quotes what the user wrote. */
  case Invalid(description: String)

final case class CreateSpec(entries: Vector[CreateEntry]) extends DirectiveSpec:
  def directive: Directive = Directive.Create

enum CleanEntry:
  case Target(path: String, force: Boolean, recursive: Boolean)

  /** An entry that could not be understood. `description` quotes what the user wrote. */
  case Invalid(description: String)

final case class CleanSpec(entries: Vector[CleanEntry]) extends DirectiveSpec:
  def directive: Directive = Directive.Clean

enum LinkEntry:
  case Link(rawLinkName: String, target: String, options: LinkOptions)
  case InvalidLinkType(linkType: UnknownLinkType)

final case class LinkSpec(defaultLinkTypeError: Option[UnknownLinkType], entries: Vector[LinkEntry])
    extends DirectiveSpec:
  def directive: Directive = Directive.Link

enum ShellEntry:
  case Command(
      command: String,
      description: String,
      quiet: Boolean,
      stdin: Boolean,
      stdout: Boolean,
      stderr: Boolean,
  )

  /** An entry that could not be understood. `description` quotes what the user wrote. */
  case Invalid(description: String)

final case class ShellSpec(entries: Vector[ShellEntry]) extends DirectiveSpec:
  def directive: Directive = Directive.Shell
