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
    else walkMatches(pattern, staticRoot(pattern), depthLimit(pattern), _ => true)

  def globLinkItem(pattern: String, item: String): String =
    val dir = PathUtil.dirname(commonPrefix(pattern, item))
    if dir == "." || dir == "/" || dir.isEmpty then item
    else item.stripPrefix(dir + "/")

  private def doubleStarGlob(pattern: String): Either[DotbotError, Vector[String]] =
    // `**` matches any number of directories, so there is no depth to stop at.
    walkMatches(pattern, staticRoot(pattern.take(pattern.indexOf("**"))), Int.MaxValue, includeDoubleStarMatch(pattern, _))

  /**
   * How deep below the static root a match could possibly be.
   *
   * A pattern without a double star can only match at one exact depth, because each `/` in the
   * pattern is one directory level. Walking the entire subtree regardless meant that a pattern
   * such as "config" followed by a single star, on a dotfiles repository containing a vendored
   * `.git` or `node_modules`, descended through every last file in them looking for candidates
   * that could never match at that depth. Handing the depth to `Files.walk` prunes the walk.
   */
  private def depthLimit(pattern: String): Int =
    val root = staticRoot(pattern)
    val rootDepth = if root == "." || root == "/" then 0 else root.count(_ == '/') + 1
    val patternDepth = pattern.count(_ == '/') + 1
    math.max(patternDepth - rootDepth, 1)

  private def walkMatches(
    pattern:     String,
    root:        String,
    maxDepth:    Int,
    includePath: Path => Boolean,
  ): Either[DotbotError, Vector[String]] =
    Try {
      val matcher = FileSystems.getDefault.getPathMatcher(s"glob:$pattern")
      if !Files.exists(Paths.get(root)) then Vector.empty
      else
        val stream = Files.walk(Paths.get(root), maxDepth)
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
