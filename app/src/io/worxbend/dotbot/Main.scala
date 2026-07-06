package io.worxbend.dotbot

import io.worxbend.dotbot.cli.Cli

object Main:
  def main(args: Array[String]): Unit =
    sys.exit(Cli.execute(args))
