package io.worxbend.dotbot.core

import java.nio.file.Paths
import scala.util.matching.Regex

/**
 * Path algebra that depends on nothing but its arguments.
 *
 * Anything that needs the user's home directory or environment variables lives in
 * [[PathResolver]] instead, so that this object stays trivially testable.
 */
object PathUtil:
  def clean(path: String): String =
    Paths.get(path).normalize().toString

  def join(first: String, more: String*): String =
    Paths.get(first, more*).normalize().toString

  def dirname(path: String): String =
    Option(Paths.get(path).getParent).map(_.toString).getOrElse(".")

  def basename(path: String): String =
    Option(Paths.get(path).getFileName).map(_.toString).getOrElse(path)

  def relative(fromDirectory: String, target: String): String =
    try Paths.get(fromDirectory).relativize(Paths.get(target)).toString
    catch case _: IllegalArgumentException => target

/**
 * Expands and resolves the paths written in a configuration file against a given [[Environment]].
 *
 * Two expansions happen here, in this order: environment variables (`$HOME`, `${HOME}`) and then
 * a leading `~`.
 */
final class PathResolver(env: Environment):
  /** Expand a leading `~` into the home directory. Any other path is returned unchanged. */
  def user(path: String): String =
    if path == "~" then env.homeDirectory
    else if path.startsWith("~/") then Paths.get(env.homeDirectory, path.drop(2)).normalize().toString
    else path

  /** Expand environment variables and then `~`. */
  def path(path: String): String =
    user(expandEnv(path))

  /** Expand, then resolve against the process working directory if still relative. */
  def abs(path: String): String =
    resolveFrom(env.workingDirectory, path)

  /** Expand, then resolve against `base` if still relative. */
  def absFrom(base: String, path: String): String =
    resolveFrom(base, path)

  /**
   * Resolve a path that has already been expanded, without expanding it a second time.
   *
   * Recursive walks build child paths out of real directory entries. Those are finished paths,
   * and a directory legitimately named `$cache` or `~` must be treated as a name, not as
   * something to expand again.
   */
  def absFromExpanded(base: String, expandedPath: String): String =
    normalizeAgainst(base, expandedPath)

  private def resolveFrom(base: String, path: String): String =
    normalizeAgainst(base, this.path(path))

  private def normalizeAgainst(base: String, expanded: String): String =
    val nio = Paths.get(expanded)
    val absolute = if nio.isAbsolute then nio else Paths.get(base, expanded)
    absolute.normalize().toString

  /**
   * Substitute `$NAME` and `${NAME}` from the environment.
   *
   * An unset variable is left in the text exactly as written. This matches Python's
   * `os.path.expandvars`, which upstream Dotbot uses, and it fails safe: substituting an empty
   * string would turn `$XDG_CONFIG_HOME/nvim` into `/nvim` and silently aim link and clean
   * operations — including destructive ones — at the filesystem root. Leaving the text alone makes
   * the path fail visibly as a nonexistent target instead.
   */
  private def expandEnv(input: String): String =
    PathResolver.EnvPattern.replaceAllIn(
      input,
      m =>
        val name = Option(m.group(1)).getOrElse(m.group(2))
        // Values are literal text, never a replacement template. Without quoting, a variable whose
        // value contains `$` or `\` is read as a group reference and either corrupts the path or
        // throws IllegalArgumentException out of the regex engine.
        Regex.quoteReplacement(env.variable(name).getOrElse(m.matched)),
    )

object PathResolver:
  private val EnvPattern: Regex = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}|\\$([A-Za-z_][A-Za-z0-9_]*)".r

  /** A resolver over the real process environment, for the composition root. */
  val system: PathResolver = PathResolver(Environment.System)
