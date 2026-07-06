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

  def createGlobResults(pattern: String, exclude: Vector[String]): Either[String, Vector[String]] =
    for
      included <- glob(pattern)
      excluded <- exclude.foldLeft(Right(Set.empty): Either[String, Set[String]]) { (acc, item) =>
        for
          current <- acc
          matches <- glob(item)
        yield current ++ matches
      }
    yield included.filterNot(excluded.contains).sorted

  def glob(pattern: String): Either[String, Vector[String]] =
    if pattern.contains("**") then doubleStarGlob(pattern)
    else
      Try {
        val matcher = FileSystems.getDefault.getPathMatcher(s"glob:$pattern")
        val root = staticRoot(pattern)
        if !Files.exists(Paths.get(root)) then Vector.empty
        else
          val stream = Files.walk(Paths.get(root))
          try
            stream.iterator().asScala
              .filter(path => matcher.matches(path))
              .map(_.normalize().toString)
              .toVector
          finally stream.close()
      }.toEither.left.map(_.getMessage)

  def globLinkItem(pattern: String, item: String): String =
    val dir = PathUtil.dirname(commonPrefix(pattern, item))
    if dir == "." || dir == "/" || dir.isEmpty then item
    else item.stripPrefix(dir + "/")

  private def doubleStarGlob(pattern: String): Either[String, Vector[String]] =
    Try {
      val matcher = FileSystems.getDefault.getPathMatcher(s"glob:$pattern")
      val root = staticRoot(pattern.take(pattern.indexOf("**")))
      if !Files.exists(Paths.get(root)) then Vector.empty
      else
        val stream = Files.walk(Paths.get(root))
        try
          stream.iterator().asScala
            .filter(path => matcher.matches(path))
            .filter(path => !Files.isDirectory(path) || pattern.endsWith("/"))
            .map(_.normalize().toString)
            .toVector
        finally stream.close()
    }.toEither.left.map(_.getMessage)

  private def staticRoot(pattern: String): String =
    val idx = pattern.indexWhere(ch => ch == '?' || ch == '*' || ch == '[')
    val prefix = if idx < 0 then pattern else pattern.take(idx)
    val slash = prefix.lastIndexOf('/')
    val root = if slash >= 0 then prefix.take(slash) else "."
    if root.isEmpty then "/" else root

  private def commonPrefix(a: String, b: String): String =
    val length = math.min(a.length, b.length)
    var i = 0
    while i < length && a.charAt(i) == b.charAt(i) do i += 1
    a.take(i)
