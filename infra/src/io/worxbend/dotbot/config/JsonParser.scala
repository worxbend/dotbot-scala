package io.worxbend.dotbot.config

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.DotbotError

/** Reads JSON with a hand-written codec, so that field order survives and no macro or reflection is involved. */
private[config] object JsonParser:
  /**
   * Parse JSON.
   *
   * jsoniter-scala reads the document as a stream of tokens, so an object's fields arrive in the
   * order they were written and the codec below simply appends them. No ordering has to be
   * recovered afterwards, unlike HOCON. The codec is written by hand rather than generated, which
   * keeps the parser free of both macros and reflection — the latter matters because this project
   * ships GraalVM native binaries.
   */
  def parse(data: String): Either[DotbotError, ConfigValue] =
    if data.trim.isEmpty then Right(ConfigValue.NullValue)
    else Right(readFromString(data)(using JsonConfigValueCodec))

  private object JsonConfigValueCodec extends JsonValueCodec[ConfigValue]:
    def nullValue: ConfigValue = ConfigValue.NullValue

    def encodeValue(value: ConfigValue, out: JsonWriter): Unit =
      // Configuration is only ever read, never written back out.
      throw UnsupportedOperationException("dotbot does not write JSON configuration")

    def decodeValue(in: JsonReader, default: ConfigValue): ConfigValue =
      val token = in.nextToken()
      in.rollbackToken()
      token match
        case '"'                                                     => ConfigValue.StringValue(in.readString(null))
        case 't' | 'f'                                               => ConfigValue.BoolValue(in.readBoolean())
        case 'n'                                                     => readNull(in)
        case '['                                                     => readArray(in, default)
        case '{'                                                     => readObject(in, default)
        case digit if digit == '-' || (digit >= '0' && digit <= '9') =>
          ConfigValue.NumberValue(in.readBigDecimal(null))
        case _                                                       => in.decodeError("expected a JSON value")

    private def readNull(in: JsonReader): ConfigValue =
      // `readNullOrError` reads the rest of the literal and so needs the token it follows to be
      // the current one; the dispatch above rolled its lookahead back.
      in.nextToken(): Unit
      in.readNullOrError(ConfigValue.NullValue, "expected null")

    private def readArray(in: JsonReader, default: ConfigValue): ConfigValue =
      if !in.isNextToken('[') then in.decodeError("expected '['")
      else if in.isNextToken(']') then ConfigValue.ArrayValue(Vector.empty)
      else
        in.rollbackToken()
        val items = Vector.newBuilder[ConfigValue]
        while
          items += decodeValue(in, default)
          in.isNextToken(',')
        do ()
        if in.isCurrentToken(']') then ConfigValue.ArrayValue(items.result())
        else in.arrayEndOrCommaError()

    private def readObject(in: JsonReader, default: ConfigValue): ConfigValue =
      if !in.isNextToken('{') then in.decodeError("expected '{'")
      else if in.isNextToken('}') then ConfigValue.ObjectValue(Vector.empty)
      else
        in.rollbackToken()
        val fields = Vector.newBuilder[(String, ConfigValue)]
        while
          // Appending as the tokens arrive is what preserves the order the user wrote.
          fields += in.readKeyAsString() -> decodeValue(in, default)
          in.isNextToken(',')
        do ()
        if in.isCurrentToken('}') then ConfigValue.ObjectValue(fields.result())
        else in.objectEndOrCommaError()
