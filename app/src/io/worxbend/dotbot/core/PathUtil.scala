package io.worxbend.dotbot.core

import java.nio.file.Path
import java.nio.file.Paths

object PathUtil:
  def normSlash(path: String): String =
    path

  def user(path: String): String =
    val normalized = normSlash(path)
    if normalized == "~" then os.home.toString
    else if normalized.startsWith("~/") then
      val tail = normalized.drop(2)
      (os.home / os.RelPath(tail)).toString
    else normalized

  def path(path: String): String =
    user(expandEnv(normSlash(path)))

  def abs(path: String): String =
    val expanded = this.path(path)
    val nio = Paths.get(expanded)
    val absolute = if nio.isAbsolute then nio else Paths.get(os.pwd.toString, expanded)
    absolute.normalize().toString

  def absFrom(base: String, path: String): String =
    val expanded = this.path(path)
    val nio = Paths.get(expanded)
    val absolute = if nio.isAbsolute then nio else Paths.get(base, expanded)
    absolute.normalize().toString

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

  private def expandEnv(input: String): String =
    val pattern = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}|\\$([A-Za-z_][A-Za-z0-9_]*)".r
    pattern.replaceAllIn(
      input,
      m =>
        val name = Option(m.group(1)).getOrElse(m.group(2))
        sys.env.getOrElse(name, ""),
    )
