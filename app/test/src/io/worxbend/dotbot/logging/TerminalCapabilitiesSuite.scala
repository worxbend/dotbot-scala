package io.worxbend.dotbot.logging

import io.worxbend.dotbot.core.Environment

/**
 * These rules used to read `sys.env` and `System.console()` directly and had no tests at all,
 * because there was no way to write one without mutating the JVM's environment.
 */
class TerminalCapabilitiesSuite extends munit.FunSuite:
  private def env(variables: (String, String)*): Environment =
    Environment.fixed(variables.toMap)

  test("an interactive terminal with nothing opted out gets both") {
    assertEquals(
      TerminalCapabilities.from(env(), consoleAttached = true),
      TerminalCapabilities(color = true, symbols = true),
    )
  }

  test("nothing is rendered when output is not going to a terminal") {
    // This is the piped and redirected case, and the reason plain output must stay plain.
    assertEquals(
      TerminalCapabilities.from(env(), consoleAttached = false),
      TerminalCapabilities.plain,
    )
  }

  test("a dumb terminal gets neither, whatever else is set") {
    assertEquals(
      TerminalCapabilities.from(env("TERM" -> "dumb"), consoleAttached = true),
      TerminalCapabilities.plain,
    )
  }

  test("TERM is matched without regard to case") {
    assertEquals(
      TerminalCapabilities.from(env("TERM" -> "DUMB"), consoleAttached = true),
      TerminalCapabilities.plain,
    )
  }

  test("NO_COLOR and NO_EMOJI are independent of each other") {
    assertEquals(
      TerminalCapabilities.from(env("NO_COLOR" -> "1"), consoleAttached = true),
      TerminalCapabilities(color = false, symbols = true),
    )
    assertEquals(
      TerminalCapabilities.from(env("NO_EMOJI" -> "1"), consoleAttached = true),
      TerminalCapabilities(color = true, symbols = false),
    )
  }

  test("the opt-out variables are honored whatever their value") {
    // NO_COLOR is a convention about presence, not about the value being truthy.
    assertEquals(
      TerminalCapabilities.from(env("NO_COLOR" -> ""), consoleAttached = true).color,
      false,
    )
    assertEquals(
      TerminalCapabilities.from(env("NO_COLOR" -> "0"), consoleAttached = true).color,
      false,
    )
  }

  test("a normal TERM does not disable anything") {
    assertEquals(
      TerminalCapabilities.from(env("TERM" -> "xterm-256color"), consoleAttached = true),
      TerminalCapabilities(color = true, symbols = true),
    )
  }
