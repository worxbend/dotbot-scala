package io.worxbend.dotbot

import io.worxbend.dotbot.testkit.TestEnvironment

/**
 * Covers path expansion against a fixed environment.
 *
 * None of these tests read the real environment, which is what makes them meaningful: expansion
 * used to call `sys.env` directly, so the only way to exercise a variable being set, unset, or
 * containing an awkward value was to mutate the JVM's global state.
 */
class PathResolverSuite extends munit.FunSuite:
  private val Home = TestEnvironment.HomeDirectory

  test("expands a bare tilde and a tilde prefix to the home directory") {
    val paths = TestEnvironment.resolver()

    assertEquals(paths.path("~"), Home)
    assertEquals(paths.path("~/.vimrc"), s"$Home/.vimrc")
  }

  test("leaves a tilde that is not a path prefix alone") {
    val paths = TestEnvironment.resolver()

    assertEquals(paths.path("~backup"), "~backup")
    assertEquals(paths.path("/tmp/~/file"), "/tmp/~/file")
  }

  test("substitutes both environment variable spellings") {
    val paths = TestEnvironment.resolver(Map("XDG_CONFIG_HOME" -> "/home/test/.config"))

    assertEquals(paths.path("$XDG_CONFIG_HOME/nvim"), "/home/test/.config/nvim")
    assertEquals(paths.path("${XDG_CONFIG_HOME}/nvim"), "/home/test/.config/nvim")
  }

  test("leaves an unset variable in place rather than collapsing the path") {
    val paths = TestEnvironment.resolver()

    // Substituting "" here would produce "/nvim" and silently aim link and clean operations at the
    // filesystem root. Leaving the text alone makes the path fail visibly instead.
    assertEquals(paths.path("$XDG_CONFIG_HOME/nvim"), "$XDG_CONFIG_HOME/nvim")
    assertEquals(paths.path("${XDG_CONFIG_HOME}/nvim"), "${XDG_CONFIG_HOME}/nvim")
    assertEquals(paths.path("before-$UNSET-after"), "before-$UNSET-after")
  }

  test("treats a variable value containing a dollar sign as literal text") {
    val paths = TestEnvironment.resolver(Map("SECRET" -> "pa$$w0rd", "GROUP" -> "pa$1ss"))

    // These went through Matcher.appendReplacement, which reads `$` as a group reference: the
    // first case threw IllegalArgumentException out of the regex engine and killed the run, and
    // the second silently swallowed "$1" and produced a corrupted path.
    assertEquals(paths.path("/opt/$SECRET/x"), "/opt/pa$$w0rd/x")
    assertEquals(paths.path("/opt/$GROUP/x"), "/opt/pa$1ss/x")
  }

  test("treats a variable value containing a backslash as literal text") {
    val paths = TestEnvironment.resolver(Map("WINPATH" -> """a\b"""))

    assertEquals(paths.path("""/opt/$WINPATH/x"""), """/opt/a\b/x""")
  }

  test("resolves relative paths against the given base and absolute paths as themselves") {
    val paths = TestEnvironment.resolver()

    assertEquals(paths.absFrom("/tmp/dotbot", "a/../b"), "/tmp/dotbot/b")
    assertEquals(paths.absFrom("/tmp/dotbot", "/etc/hosts"), "/etc/hosts")
  }

  test("resolves relative paths against the working directory") {
    val paths = TestEnvironment.resolver()

    assertEquals(paths.abs("nested/file"), s"${TestEnvironment.WorkingDirectory}/nested/file")
  }

  test("expands while resolving, so a config path may reference a variable") {
    val paths = TestEnvironment.resolver(Map("DOTFILES" -> "/srv/dotfiles"))

    assertEquals(paths.absFrom("/tmp/dotbot", "$DOTFILES/vimrc"), "/srv/dotfiles/vimrc")
  }

  test("absFromExpanded resolves without expanding a second time") {
    val paths = TestEnvironment.resolver(Map("cache" -> "/var/cache"))

    // A directory really named "$cache" is a name, not something to substitute. Recursive walks
    // feed real directory entries back in, so re-expanding them would rewrite the path being
    // inspected into something else entirely.
    assertEquals(paths.absFromExpanded("/tmp/dotbot", "$cache"), "/tmp/dotbot/$cache")
    assertEquals(paths.absFromExpanded("/tmp/dotbot", "~"), "/tmp/dotbot/~")
    assertEquals(paths.absFrom("/tmp/dotbot", "$cache"), "/var/cache")
  }
