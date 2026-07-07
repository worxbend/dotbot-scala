package io.worxbend.dotbot.core

opaque type FileMode = Int

object FileMode:
  val ownerRead: FileMode = octalLiteral("400")
  val ownerWrite: FileMode = octalLiteral("200")
  val ownerExecute: FileMode = octalLiteral("100")
  val groupRead: FileMode = octalLiteral("040")
  val groupWrite: FileMode = octalLiteral("020")
  val groupExecute: FileMode = octalLiteral("010")
  val othersRead: FileMode = octalLiteral("004")
  val othersWrite: FileMode = octalLiteral("002")
  val othersExecute: FileMode = octalLiteral("001")

  val rwxAll: FileMode = octalLiteral("777")

  def apply(value: Int): FileMode = value

  def fromOctal(value: String): Option[FileMode] =
    scala.util.Try(octalLiteral(value)).toOption

  def fromConfig(value: Option[ConfigValue], fallback: FileMode): FileMode =
    value match
      case Some(ConfigValue.StringValue(item)) =>
        fromOctal(item).getOrElse(fallback)
      case Some(ConfigValue.NumberValue(item)) => FileMode(item.toInt)
      case _                                   => fallback

  def decodeConfig(value: Option[ConfigValue], fallback: FileMode, message: String): Either[DotbotError, FileMode] =
    value match
      case None                               => Right(fallback)
      case Some(ConfigValue.StringValue(item)) => fromOctal(item).toRight(DotbotError.Decode(message))
      case Some(ConfigValue.NumberValue(item)) => Right(FileMode(item.toInt))
      case Some(_)                            => Left(DotbotError.Decode(message))

  private def octalLiteral(value: String): FileMode =
    FileMode(Integer.parseInt(value, 8))

  extension (mode: FileMode) def value: Int = mode
