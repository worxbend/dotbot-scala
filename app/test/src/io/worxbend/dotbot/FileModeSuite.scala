package io.worxbend.dotbot

import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.DotbotError
import io.worxbend.dotbot.core.FileMode

class FileModeSuite extends munit.FunSuite:
  private def octal(value: String): Int =
    Integer.parseInt(value, 8)

  test("parses octal strings and rejects text that is not octal") {
    assertEquals(FileMode.fromOctal("755").map(_.value), Some(octal("755")))
    assertEquals(FileMode.fromOctal("0755").map(_.value), Some(octal("755")))
    assertEquals(FileMode.fromOctal("abc"), None)
    // 8 and 9 are not octal digits, so this is not a mode anyone can mean.
    assertEquals(FileMode.fromOctal("800"), None)
  }

  test("reads a bare number as octal digits, not as a decimal value") {
    // The whole point: `mode: 0755` in YAML arrives as the number 755. Taken as decimal its low
    // nine bits are 0o363 — `-wxrw--wx`, unreadable by its owner and writable by everyone else.
    assertEquals(FileMode.fromNumber(BigDecimal(755)).map(_.value), Some(octal("755")))
    assertEquals(FileMode.fromNumber(BigDecimal(644)).map(_.value), Some(octal("644")))
    assertNotEquals(FileMode.fromNumber(BigDecimal(755)).map(_.value), Some(755))
  }

  test("rejects numbers that cannot be a mode") {
    assertEquals(FileMode.fromNumber(BigDecimal(800)), None)
    assertEquals(FileMode.fromNumber(BigDecimal(-1)), None)
    assertEquals(FileMode.fromNumber(BigDecimal("7.5")), None)
  }

  test("decodes both config spellings of the same mode to the same value") {
    val fallback = FileMode.rwxAll
    val fromString = FileMode.decodeConfig(Some(ConfigValue.StringValue("0755")), fallback, "bad")
    val fromNumber = FileMode.decodeConfig(Some(ConfigValue.NumberValue(BigDecimal(755))), fallback, "bad")

    assertEquals(fromString.map(_.value), Right(octal("755")))
    assertEquals(fromNumber.map(_.value), fromString.map(_.value))
  }

  test("strict decoding reports an error while lenient decoding falls back") {
    val fallback = FileMode.rwxAll

    assertEquals(
      FileMode.decodeConfig(Some(ConfigValue.StringValue("abc")), fallback, "bad mode"),
      Left(DotbotError.Decode("bad mode")),
    )
    assertEquals(
      FileMode.decodeConfig(Some(ConfigValue.BoolValue(true)), fallback, "bad mode"),
      Left(DotbotError.Decode("bad mode")),
    )
    assertEquals(FileMode.fromConfig(Some(ConfigValue.StringValue("abc")), fallback).value, fallback.value)
    assertEquals(FileMode.fromConfig(Some(ConfigValue.NumberValue(BigDecimal(800))), fallback).value, fallback.value)
  }

  test("a missing mode keeps the fallback in both modes") {
    val fallback = FileMode.fromOctal("700").get

    assertEquals(FileMode.decodeConfig(None, fallback, "bad").map(_.value), Right(octal("700")))
    assertEquals(FileMode.fromConfig(None, fallback).value, octal("700"))
  }

  test("named permission bits compose into the modes they describe") {
    val ownerAll = FileMode.ownerRead.value | FileMode.ownerWrite.value | FileMode.ownerExecute.value

    assertEquals(ownerAll, octal("700"))
    assertEquals(FileMode.rwxAll.value, octal("777"))
  }
