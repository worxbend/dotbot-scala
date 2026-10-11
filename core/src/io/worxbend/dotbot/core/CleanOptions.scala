package io.worxbend.dotbot.core

/**
 * What a `clean` entry is allowed to remove.
 *
 * `force` also removes links that point somewhere outside the base directory; `recursive` descends
 * into subdirectories rather than only checking the directory named.
 */
final case class CleanOptions(force: Boolean = false, recursive: Boolean = false)

object CleanOptions:
  /** The options a `defaults` block establishes for every later `clean` entry. */
  def fromDefaults(values: Map[String, ConfigValue]): CleanOptions =
    merge(CleanOptions(), values)

  /** Apply one entry's own settings on top of the defaults in force. */
  def merge(options: CleanOptions, values: Map[String, ConfigValue]): CleanOptions =
    CleanOptions(
      force = values.boolValue("force", options.force),
      recursive = values.boolValue("recursive", options.recursive),
    )
