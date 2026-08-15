package io.worxbend.dotbot

import io.worxbend.dotbot.core.Glob

/**
 * Globbing runs against the real filesystem, so these tests build a small tree in a temporary
 * directory. They pin what a pattern may match at each depth, which is what the walk bound relies
 * on being right: a bound that is too tight would silently drop matches.
 */
class GlobDepthSuite extends munit.FunSuite:
  private def tree(): os.Path =
    val root = os.temp.dir(prefix = "dotbot-scala-glob-depth-")
    os.write(root / "top.txt", "top", createFolders = true)
    os.write(root / "config" / "alpha.conf", "a", createFolders = true)
    os.write(root / "config" / "beta.conf", "b", createFolders = true)
    os.write(root / "config" / "nvim" / "init.lua", "n", createFolders = true)
    os.write(root / "config" / "nvim" / "lua" / "deep.lua", "d", createFolders = true)
    root

  private def globbed(pattern: String): Vector[String] =
    Glob.glob(pattern).fold(error => fail(error.render), identity).sorted

  test("a single-star pattern matches only its own depth") {
    val root = tree()

    val matches = globbed(s"$root/config/*")

    // Files one level down are matched; anything deeper is not, so there is no reason to have
    // walked into it.
    assertEquals(
      matches.map(_.stripPrefix(s"$root/")),
      Vector("config/alpha.conf", "config/beta.conf", "config/nvim"),
    )
  }

  test("a deeper single-star pattern still reaches its depth") {
    val root = tree()

    assertEquals(
      globbed(s"$root/config/nvim/*").map(_.stripPrefix(s"$root/")),
      Vector("config/nvim/init.lua", "config/nvim/lua"),
    )
  }

  test("a double-star pattern reaches every depth below the root") {
    val root = tree()

    val matches = globbed(s"$root/config/**").map(_.stripPrefix(s"$root/"))

    assert(matches.contains("config/alpha.conf"), matches)
    assert(matches.contains("config/nvim/lua/deep.lua"), matches)
  }

  test("a pattern with no static root still matches at the top level") {
    val root = tree()

    assertEquals(globbed(s"$root/*.txt").map(_.stripPrefix(s"$root/")), Vector("top.txt"))
  }

  test("a pattern under a directory that does not exist matches nothing") {
    val root = tree()

    assertEquals(globbed(s"$root/absent/*"), Vector.empty)
  }

  test("excludes are removed from the results and the rest stay sorted") {
    val root = tree()

    val matches = Glob
      .createGlobResults(s"$root/config/*", Vector(s"$root/config/beta.conf"))
      .fold(error => fail(error.render), identity)

    assertEquals(
      matches.map(_.stripPrefix(s"$root/")),
      Vector("config/alpha.conf", "config/nvim"),
    )
  }

  test("an exclude pattern may itself be a glob") {
    val root = tree()

    val matches = Glob
      .createGlobResults(s"$root/config/*", Vector(s"$root/config/*.conf"))
      .fold(error => fail(error.render), identity)

    assertEquals(matches.map(_.stripPrefix(s"$root/")), Vector("config/nvim"))
  }
