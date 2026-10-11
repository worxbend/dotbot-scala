package io.worxbend.dotbot.core

final case class DirectiveDefaults(values: Map[Directive, ConfigValue]):
  def section(directive: Directive): Map[String, ConfigValue] =
    values.get(directive).flatMap(_.asMap).getOrElse(Map.empty)

object DirectiveDefaults:
  val empty: DirectiveDefaults = DirectiveDefaults(Map.empty)

  /** Compatibility decoding for apply. Validation checks the complete block before information is lost. */
  def from(value: ConfigValue): DirectiveDefaults =
    DirectiveDefaults(
      value.asMap.getOrElse(Map.empty).flatMap { case (name, section) =>
        Directive.fromString(name).map(_ -> section)
      },
    )

  def decode(value: ConfigValue): Either[DotbotError, DirectiveDefaults] =
    for
      fields   <- ConfigDecoder.asFields(value, "defaults directive must be a map")
      sections <- EitherUtil.traverse(fields.keys)(name => decodeSection(name, fields(name)))
    yield DirectiveDefaults(sections.toMap)

  private def decodeSection(name: String, value: ConfigValue): Either[DotbotError, (Directive, ConfigValue)] =
    for
      directive <- Directive.fromString(name).toRight(DotbotError.Decode(s"unknown defaults section \"$name\""))
      schema    <-
        OptionSchema.forDirective(directive).toRight(DotbotError.Decode(s"unsupported defaults section \"$name\""))
      options   <- ConfigDecoder.asMap(value, s"defaults section \"$name\" must be a map")
      _         <- schema.validate(options, OptionContext.Defaults(name))
      _         <- validateLinkType(directive, options)
    yield directive -> value

  private def validateLinkType(directive: Directive, options: Map[String, ConfigValue]): Either[DotbotError, Unit] =
    if directive != Directive.Link then Right(())
    else
      LinkOptions
        .fromDefaults(options)
        .left
        .map(error => DotbotError.Decode(s"default link type is not recognized: ${error.render}"))
        .map(_ => ())
