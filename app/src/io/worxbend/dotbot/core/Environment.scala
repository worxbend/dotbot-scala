package io.worxbend.dotbot.core

/**
 * The ambient process environment: variables plus the two well-known directories that path
 * expansion depends on.
 *
 * This exists so that nothing below the composition root reads `sys.env` or `sys.props` directly.
 * Those are global mutable state: code that reads them cannot be tested without mutating the whole
 * JVM, and two tests that do so cannot run in parallel. Everything that needs the environment now
 * receives one of these instead.
 */
trait Environment:
  /** The value of an environment variable, or `None` when it is not set. */
  def variable(name: String): Option[String]

  /** The current user's home directory, the expansion of a leading `~`. */
  def homeDirectory: String

  /** The directory the process was started in, used to resolve relative paths. */
  def workingDirectory: String

object Environment:
  /**
   * The real environment of the running process. This is the only place in the codebase that
   * reads `sys.env` or `sys.props`; it is wired in at the composition root.
   */
  object System extends Environment:
    def variable(name: String): Option[String] = sys.env.get(name)
    def homeDirectory: String                  = sys.props("user.home")
    def workingDirectory: String               = sys.props("user.dir")

  /**
   * A fixed environment, for tests and for any caller that needs expansion to be reproducible.
   */
  def fixed(
      variables: Map[String, String] = Map.empty,
      homeDirectory: String = "/home/test",
      workingDirectory: String = "/workspace",
  ): Environment =
    val home    = homeDirectory
    val working = workingDirectory
    new Environment:
      def variable(name: String): Option[String] = variables.get(name)
      def homeDirectory: String                  = home
      def workingDirectory: String               = working
