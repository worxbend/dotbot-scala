package io.worxbend.dotbot

import io.worxbend.dotbot.core.Glob
import io.worxbend.dotbot.core.PathUtil
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

import java.nio.file.Paths

class CorePropertySuite extends munit.ScalaCheckSuite:
  private val Segment: Gen[String] =
    Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

  private val RelativePath: Gen[String] =
    Gen.nonEmptyListOf(Segment).map(_.mkString("/"))

  private val PlainPathText: Gen[String] =
    Gen.listOf(Gen.oneOf(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Seq('/', '.', '_', '-'))).map(_.mkString)

  private val GlobTrigger: Gen[Char] =
    Gen.choose(0, 2).map(Vector('*', '?', '['))

  property("relative paths resolve back to the original target") {
    forAll(RelativePath, RelativePath) { (baseSuffix, targetSuffix) =>
      val base     = Paths.get("/workspace", baseSuffix).normalize().toString
      val target   = Paths.get("/workspace", targetSuffix).normalize().toString
      val relative = PathUtil.relative(base, target)

      assertEquals(Paths.get(base).resolve(relative).normalize().toString, target)
    }
  }

  property("glob detection ignores path text without glob metacharacters") {
    forAll(PlainPathText) { path =>
      assert(!Glob.hasGlobChars(path))
    }
  }

  property("glob detection recognizes each supported metacharacter") {
    forAll(PlainPathText, GlobTrigger, PlainPathText) { (prefix, char, suffix) =>
      assert(Glob.hasGlobChars(s"$prefix$char$suffix"))
    }
  }
