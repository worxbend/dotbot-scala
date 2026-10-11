package io.worxbend.dotbot.core

sealed trait DirectiveSpec

enum DecodeMode:
  case Validate, Execute

  def strict: Boolean =
    this == DecodeMode.Validate

enum CreateEntry:
  /** No explicit mode means the filesystem preserves the process umask. */
  case Path(path: String, mode: Option[FileMode])

  /** An entry that could not be understood. `description` quotes what the user wrote. */
  case Invalid(description: String)

final case class CreateSpec(entries: Vector[CreateEntry]) extends DirectiveSpec

enum CleanEntry:
  case Target(path: String, options: CleanOptions)

  /** An entry that could not be understood. `description` quotes what the user wrote. */
  case Invalid(description: String)

final case class CleanSpec(entries: Vector[CleanEntry]) extends DirectiveSpec

enum LinkEntry:
  case Link(rawLinkName: String, target: String, options: LinkOptions)
  case InvalidLinkType(linkType: UnknownLinkType)

final case class LinkSpec(defaultLinkTypeError: Option[UnknownLinkType], entries: Vector[LinkEntry])
    extends DirectiveSpec

enum ShellEntry:
  case Command(command: String, description: String, options: ShellEntryOptions)

  /** An entry that could not be understood. `description` quotes what the user wrote. */
  case Invalid(description: String)

final case class ShellSpec(entries: Vector[ShellEntry]) extends DirectiveSpec
