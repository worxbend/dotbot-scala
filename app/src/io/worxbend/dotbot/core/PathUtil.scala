package io.worxbend.dotbot.core

import java.nio.file.Paths

object PathUtil:
  private val EnvPattern = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}|\\$([A-Za-z_][A-Za-z0-9_]*)".r

  def user(path: String): String =
    if path == "~" then homeDirectory
    else if path.startsWith("~/") then
      val tail = path.drop(2)
      Paths.get(homeDirectory, tail).normalize().toString
    else path

  def path(path: String): String =
    user(expandEnv(path))

  def abs(path: String): String =
    resolveFrom(workingDirectory, path)

  def absFrom(base: String, path: String): String =
    resolveFrom(base, path)

  private def resolveFrom(base: String, path: String): String =
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
    EnvPattern.replaceAllIn(
      input,
      m =>
        val name = Option(m.group(1)).getOrElse(m.group(2))
        sys.env.getOrElse(name, ""),
    )

  private def homeDirectory: String =
    sys.props("user.home")

  private def workingDirectory: String =
    sys.props("user.dir")
