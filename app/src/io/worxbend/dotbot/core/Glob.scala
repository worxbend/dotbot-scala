package io.worxbend.dotbot.core

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*
import scala.util.Try

object Glob:
  def hasGlobChars(path: String): Boolean =
    path.exists(ch => ch == '?' || ch == '*' || ch == '[')

  def createGlobResults(pattern: String, exclude: Vector[String]): Either[DotbotError, Vector[String]] =
    for
      included <- glob(pattern)
      excluded <- EitherUtil.traverse(exclude)(glob).map(_.flatten.toSet)
    yield included.filterNot(excluded.contains).sorted

  def glob(pattern: String): Either[DotbotError, Vector[String]] =
    if pattern.contains("**") then doubleStarGlob(pattern)
    else walkMatches(pattern, staticRoot(pattern), _ => true)

  def globLinkItem(pattern: String, item: String): String =
    val dir = PathUtil.dirname(commonPrefix(pattern, item))
    if dir == "." || dir == "/" || dir.isEmpty then item
    else item.stripPrefix(dir + "/")

  private def doubleStarGlob(pattern: String): Either[DotbotError, Vector[String]] =
    walkMatches(pattern, staticRoot(pattern.take(pattern.indexOf("**"))), includeDoubleStarMatch(pattern, _))

  private def walkMatches(pattern: String, root: String, includePath: Path => Boolean): Either[DotbotError, Vector[String]] =
    Try {
      val matcher = FileSystems.getDefault.getPathMatcher(s"glob:$pattern")
      if !Files.exists(Paths.get(root)) then Vector.empty
      else
        val stream = Files.walk(Paths.get(root))
        try
          stream.iterator().asScala
            .filter(path => matcher.matches(path))
            .filter(includePath)
            .map(_.normalize().toString)
            .toVector
        finally stream.close()
    }.toEither.left.map(error => DotbotError.Message(error.getMessage))

  /** Characterized by GlobSuite: double-star patterns exclude directories unless the pattern ends with "/". */
  private def includeDoubleStarMatch(pattern: String, path: Path): Boolean =
    !Files.isDirectory(path) || pattern.endsWith("/")

  private def staticRoot(pattern: String): String =
    val idx = pattern.indexWhere(ch => ch == '?' || ch == '*' || ch == '[')
    val prefix = if idx < 0 then pattern else pattern.take(idx)
    val slash = prefix.lastIndexOf('/')
    val root = if slash >= 0 then prefix.take(slash) else "."
    if root.isEmpty then "/" else root

  private def commonPrefix(a: String, b: String): String =
    val length = a.iterator.zip(b.iterator).takeWhile { case (left, right) => left == right }.length
    a.take(length)
