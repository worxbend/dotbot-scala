package io.worxbend.dotbot.core

trait ConfigDecoder[A]:
  def decode(value: ConfigValue, defaults: DirectiveDefaults, mode: DecodeMode): Either[DotbotError, A]

object ConfigDecoder:
  def apply[A](using decoder: ConfigDecoder[A]): ConfigDecoder[A] =
    decoder

  def decodeError(message: String): DotbotError =
    DotbotError.Decode(message)

  def fail[A](message: String): Either[DotbotError, A] =
    Left(decodeError(message))

  def instance[A](
      decodeValue: (ConfigValue, DirectiveDefaults, DecodeMode) => Either[DotbotError, A],
  ): ConfigDecoder[A] =
    new ConfigDecoder[A]:
      def decode(value: ConfigValue, defaults: DirectiveDefaults, mode: DecodeMode): Either[DotbotError, A] =
        decodeValue(value, defaults, mode)

  def asArray(value: ConfigValue, message: String): Either[DotbotError, Vector[ConfigValue]] =
    value.asArray.toRight(decodeError(message))

  def asMap(value: ConfigValue, message: String): Either[DotbotError, Map[String, ConfigValue]] =
    value.asMap.toRight(decodeError(message))

  /** Like [[asMap]], but keeping the order the fields were written in. */
  def asFields(value: ConfigValue, message: String): Either[DotbotError, ConfigFields] =
    value.asFields.toRight(decodeError(message))

  def fieldsOrList[A](
      value: ConfigValue,
      message: String,
  )(
      decodeFields: ConfigFields => Either[DotbotError, A],
      decodeList: Vector[ConfigValue] => Either[DotbotError, A],
  ): Either[DotbotError, A] =
    value.asFields match
      case Some(fields) => decodeFields(fields)
      case None         =>
        value.asArray match
          case Some(items) => decodeList(items)
          case None        => fail(message)

  def field[A](
      values: Map[String, ConfigValue],
      name: String,
      missing: String,
  )(decode: ConfigValue => Either[DotbotError, A]): Either[DotbotError, A] =
    values.get(name) match
      case Some(value) => decode(value)
      case None        => fail(missing)

  def optional[A](
      values: Map[String, ConfigValue],
      name: String,
  )(decode: ConfigValue => Either[DotbotError, A]): Either[DotbotError, Option[A]] =
    values.get(name) match
      case Some(value) => decode(value).map(Some(_))
      case None        => Right(None)

  def oneOf[A](value: ConfigValue)(decoders: (ConfigValue => Option[A])*): Option[A] =
    decoders.iterator.flatMap(decode => decode(value)).nextOption()
