package io.worxbend.dotbot.core

import java.nio.file.FileSystems
import java.nio.file.Paths

/**
 * Expansion of the glob patterns a `link` entry may use as its source.
 *
 * Directory traversal goes through the [[Filesystem]] port rather than `java.nio.file.Files`.
 * Reaching for the real disk from here compiled — `java.nio` is part of the JDK, so the build's
 * "core has no dependencies" rule could not catch it — but it meant the fake filesystem the rest
 * of the handlers are tested against was bypassed, and the globbing paths in `LinkHandler` had no
 * unit tests at all. What is left here is pattern algebra, which depends on nothing but its
 * arguments.
 */
object Glob:
  def hasGlobChars(path: String): Boolean =
    path.exists(ch => ch == '?' || ch == '*' || ch == '[')

  def createGlobResults(fs: Filesystem, pattern: String, exclude: Vector[String]): Either[DotbotError, Vector[String]] =
    for
      included <- glob(fs, pattern)
      excluded <- EitherUtil.traverse(exclude)(glob(fs, _)).map(_.flatten.toSet)
    yield included.filterNot(excluded.contains).sorted

  def glob(fs: Filesystem, pattern: String): Either[DotbotError, Vector[String]] =
    if pattern.contains("**") then doubleStarGlob(fs, pattern)
    else walkMatches(fs, pattern, staticRoot(pattern), depthLimit(pattern), _ => true)

  def globLinkItem(pattern: String, item: String): String =
    val dir = PathUtil.dirname(commonPrefix(pattern, item))
    if dir == "." || dir == "/" || dir.isEmpty then item
    else item.stripPrefix(dir + "/")

  private def doubleStarGlob(fs: Filesystem, pattern: String): Either[DotbotError, Vector[String]] =
    // `**` matches any number of directories, so there is no depth to stop at.
    walkMatches(
      fs,
      pattern,
      staticRoot(pattern.take(pattern.indexOf("**"))),
      Int.MaxValue,
      includeDoubleStarMatch(fs, pattern, _),
    )

  /**
   * How deep below the static root a match could possibly be.
   *
   * A pattern without a double star can only match at one exact depth, because each `/` in the
   * pattern is one directory level. Walking the entire subtree regardless meant that a pattern
   * such as "config" followed by a single star, on a dotfiles repository containing a vendored
   * `.git` or `node_modules`, descended through every last file in them looking for candidates
   * that could never match at that depth. Handing the depth to the walk prunes it.
   */
  private[core] def depthLimit(pattern: String): Int =
    val root         = staticRoot(pattern)
    val rootDepth    = if root == "." || root == "/" then 0 else root.count(_ == '/') + 1
    val patternDepth = pattern.count(_ == '/') + 1
    math.max(patternDepth - rootDepth, 1)

  private def walkMatches(
      fs: Filesystem,
      pattern: String,
      root: String,
      maxDepth: Int,
      includePath: String => Boolean,
  ): Either[DotbotError, Vector[String]] =
    val matcher = FileSystems.getDefault.getPathMatcher(s"glob:$pattern")
    fs.walk(root, maxDepth)
      .map(_.filter(path => matcher.matches(Paths.get(path))).filter(includePath))
      .left
      .map(error => DotbotError.Message(error.message))

  /** Characterized by GlobSuite: double-star patterns exclude directories unless the pattern ends with "/". */
  private def includeDoubleStarMatch(fs: Filesystem, pattern: String, path: String): Boolean =
    !fs.isDir(path) || pattern.endsWith("/")

  private[core] def staticRoot(pattern: String): String =
    val idx    = pattern.indexWhere(ch => ch == '?' || ch == '*' || ch == '[')
    val prefix = if idx < 0 then pattern else pattern.take(idx)
    val slash  = prefix.lastIndexOf('/')
    val root   = if slash >= 0 then prefix.take(slash) else "."
    if root.isEmpty then "/" else root

  private def commonPrefix(a: String, b: String): String =
    val length = a.iterator.zip(b.iterator).takeWhile { case (left, right) => left == right }.length
    a.take(length)
