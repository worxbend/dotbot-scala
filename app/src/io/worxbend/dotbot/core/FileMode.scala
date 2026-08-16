package io.worxbend.dotbot.core

opaque type FileMode = Int

object FileMode:
  val ownerRead: FileMode     = octalLiteral("400")
  val ownerWrite: FileMode    = octalLiteral("200")
  val ownerExecute: FileMode  = octalLiteral("100")
  val groupRead: FileMode     = octalLiteral("040")
  val groupWrite: FileMode    = octalLiteral("020")
  val groupExecute: FileMode  = octalLiteral("010")
  val othersRead: FileMode    = octalLiteral("004")
  val othersWrite: FileMode   = octalLiteral("002")
  val othersExecute: FileMode = octalLiteral("001")

  val rwxAll: FileMode = octalLiteral("777")

  def apply(value: Int): FileMode = value

  def fromOctal(value: String): Option[FileMode] =
    scala.util.Try(octalLiteral(value)).toOption

  /**
   * Read a mode written as a bare number, such as `mode: 0755`.
   *
   * File modes are octal by universal convention, and a YAML or JSON parser hands them over as a
   * plain decimal integer. Taking that integer at face value is dangerous: `0755` would become
   * decimal 755, whose low nine bits are `0o363` — `-wxrw--wx`, which denies the owner read access
   * while granting write to group and others. Upstream Dotbot reads the same text as octal 493, so
   * a ported config would silently end up with group- and world-writable directories.
   *
   * The digits are therefore re-read in base 8, which also rejects impossible modes such as
   * `mode: 800`.
   */
  def fromNumber(value: BigDecimal): Option[FileMode] =
    if !value.isWhole || value < 0 then None
    else fromOctal(value.toBigInt.toString)

  def fromConfig(value: Option[ConfigValue], fallback: FileMode): FileMode =
    value match
      case Some(ConfigValue.StringValue(item)) => fromOctal(item).getOrElse(fallback)
      case Some(ConfigValue.NumberValue(item)) => fromNumber(item).getOrElse(fallback)
      case _                                   => fallback

  def decodeConfig(value: Option[ConfigValue], fallback: FileMode, message: String): Either[DotbotError, FileMode] =
    value match
      case None                                => Right(fallback)
      case Some(ConfigValue.StringValue(item)) => fromOctal(item).toRight(DotbotError.Decode(message))
      case Some(ConfigValue.NumberValue(item)) => fromNumber(item).toRight(DotbotError.Decode(message))
      case Some(_)                             => Left(DotbotError.Decode(message))

  private def octalLiteral(value: String): FileMode =
    FileMode(Integer.parseInt(value, 8))

  extension (mode: FileMode) def value: Int = mode
