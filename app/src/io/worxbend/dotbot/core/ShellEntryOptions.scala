package io.worxbend.dotbot.core

/**
 * How a `shell` entry is reported and which of its streams are connected.
 *
 * Named `ShellEntryOptions` rather than `ShellOptions` because that name already belongs to the
 * runtime record the shell port takes -- this is what the config file asked for, that is what the
 * process is actually given.
 */
final case class ShellEntryOptions(
    quiet: Boolean = false,
    stdin: Boolean = false,
    stdout: Boolean = false,
    stderr: Boolean = false,
)

object ShellEntryOptions:
  /** The options a `defaults` block establishes for every later `shell` entry. */
  def fromDefaults(values: Map[String, ConfigValue]): ShellEntryOptions =
    merge(ShellEntryOptions(), values)

  /** Apply one entry's own settings on top of the defaults in force. */
  def merge(options: ShellEntryOptions, values: Map[String, ConfigValue]): ShellEntryOptions =
    ShellEntryOptions(
      quiet = values.boolValue("quiet", options.quiet),
      stdin = values.boolValue("stdin", options.stdin),
      stdout = values.boolValue("stdout", options.stdout),
      stderr = values.boolValue("stderr", options.stderr),
    )
