package io.worxbend.dotbot.cli

import io.worxbend.dotbot.app.AppOptions
import io.worxbend.dotbot.app.DotbotApp

import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.ISetter
import picocli.CommandLine.Model.OptionSpec

import java.io.PrintStream
import java.io.PrintWriter

private final class CliState:
  var quiet: Boolean = false
  var verbose: Int = 0
  var baseDirectory: String = ""
  val configFiles = scala.collection.mutable.ArrayBuffer.empty[String]
  val only = scala.collection.mutable.ArrayBuffer.empty[String]
  val skip = scala.collection.mutable.ArrayBuffer.empty[String]
  var forceColor: Boolean = false
  var noColor: Boolean = false
  var dryRun: Boolean = false
  var exitOnFailure: Boolean = false
  var output: String = "text"

  def mergedWith(parent: CliState): CliState =
    val out = CliState()
    out.quiet = parent.quiet || quiet
    out.verbose = parent.verbose + verbose
    out.baseDirectory = if baseDirectory.nonEmpty then baseDirectory else parent.baseDirectory
    out.configFiles ++= parent.configFiles
    out.configFiles ++= configFiles
    out.only ++= parent.only
    out.only ++= only
    out.skip ++= parent.skip
    out.skip ++= skip
    out.forceColor = parent.forceColor || forceColor
    out.noColor = parent.noColor || noColor
    out.dryRun = parent.dryRun || dryRun
    out.exitOnFailure = parent.exitOnFailure || exitOnFailure
    out.output = if output != "text" then output else parent.output
    out

  def appOptions(validate: Boolean = false, plan: Boolean = false): AppOptions =
    AppOptions(
      quiet = quiet,
      verbose = verbose,
      baseDirectory = baseDirectory,
      configFiles = configFiles.toVector,
      only = only.toSet,
      skip = skip.toSet,
      dryRun = dryRun,
      forceColor = forceColor,
      noColor = noColor,
      exitOnFailure = exitOnFailure,
      validate = validate,
      plan = plan,
      output = output,
    )

object Cli:
  def execute(args: Array[String], stdout: PrintStream = System.out, stderr: PrintStream = System.err): Int =
    val rootState = CliState()
    val validateState = CliState()
    val planState = CliState()

    val root = CommandSpec.create()
      .name("dotbot-scala")
      .version(s"Dotbot-Scala version ${DotbotApp.version}")
    root.usageMessage().description("A Scala 3 port of Dotbot for bootstrapping dotfiles")
    addHelpOptions(root)
    addCommonOptions(root, rootState)
    addApplyOptions(root, rootState)

    val validate = CommandSpec.create()
      .name("validate")
    validate.usageMessage().description("Validate configuration without applying changes")
    addHelpOptions(validate)
    addCommonOptions(validate, validateState)

    val plan = CommandSpec.create()
      .name("plan")
    plan.usageMessage().description("Print planned operations without applying changes")
    addHelpOptions(plan)
    addCommonOptions(plan, planState)
    plan.addOption(
      stringOption(
        Array("--output"),
        "output format: text or json",
        value => planState.output = value,
      ),
    )

    root.addSubcommand("validate", validate)
    root.addSubcommand("plan", plan)

    val command = CommandLine(root)
    command.setOverwrittenOptionsAllowed(true)
    command.setOut(PrintWriter(stdout, true))
    command.setErr(PrintWriter(stderr, true))
    command.setExecutionStrategy { parseResult =>
      if parseResult.isUsageHelpRequested || parseResult.isVersionHelpRequested then
        CommandLine.executeHelpRequest(parseResult)
      else if parseResult.hasSubcommand then
        val sub = parseResult.subcommand()
        sub.commandSpec().name() match
          case "validate" =>
            val state = withBooleanPresence(validateState.mergedWith(rootState), args, parseResult, Some(sub))
            DotbotApp.run(state.appOptions(validate = true), stdout)
          case "plan" =>
            val state = withBooleanPresence(planState.mergedWith(rootState), args, parseResult, Some(sub))
            DotbotApp.run(state.appOptions(plan = true), stdout)
          case _ => 1
      else DotbotApp.run(withBooleanPresence(rootState, args, parseResult, None).appOptions(), stdout)
    }
    command.execute(args*)

  private def addCommonOptions(spec: CommandSpec, state: CliState): Unit =
    spec.addOption(booleanOption(Array("-q", "--quiet"), "suppress most output"))
    spec.addOption(verboseOption(state))
    spec.addOption(
      stringOption(
        Array("-d", "--base-directory"),
        "execute commands from within BASE_DIR",
        value => state.baseDirectory = value,
        "BASE_DIR",
      ),
    )
    spec.addOption(
      stringOption(
        Array("-c", "--config-file"),
        "run commands given in CONFIG_FILE",
        value => state.configFiles += value,
        "CONFIG_FILE",
      ),
    )
    spec.addOption(
      stringOption(
        Array("--only"),
        "only run specified directives",
        value => state.only ++= splitList(value),
      ),
    )
    spec.addOption(
      stringOption(
        Array("--except"),
        "skip specified directives",
        value => state.skip ++= splitList(value),
      ),
    )
    spec.addOption(booleanOption(Array("--force-color"), "force color output"))
    spec.addOption(booleanOption(Array("--no-color"), "disable color output"))

  private def addHelpOptions(spec: CommandSpec): Unit =
    spec.addOption(
      OptionSpec.builder(Array("-h", "--help"))
        .description("Show this help message and exit.")
        .usageHelp(true)
        .build(),
    )
    spec.addOption(
      OptionSpec.builder(Array("-V", "--version"))
        .description("Print version information and exit.")
        .versionHelp(true)
        .build(),
    )

  private def addApplyOptions(spec: CommandSpec, state: CliState): Unit =
    spec.addOption(booleanOption(Array("-n", "--dry-run"), "print what would be done, without doing it"))
    spec.addOption(booleanOption(Array("-x", "--exit-on-failure"), "exit after first failed directive"))

  private def verboseOption(state: CliState): OptionSpec =
    OptionSpec.builder(Array("-v", "--verbose"))
      .description("-v: show informational messages; -vv: also stream shell command output")
      .`type`(classOf[java.lang.Boolean])
      .arity("0")
      .build()

  private def booleanOption(names: Array[String], description: String): OptionSpec =
    OptionSpec.builder(names)
      .description(description)
      .`type`(classOf[java.lang.Boolean])
      .arity("0")
      .build()

  private def stringOption(
    names:       Array[String],
    description: String,
    update:      String => Unit,
    paramLabel:  String = "",
  ): OptionSpec =
    val builder = OptionSpec.builder(names)
      .description(description)
      .`type`(classOf[String])
      .setter(setter(value => if value != null then update(String.valueOf(value))))
    if paramLabel.nonEmpty then builder.paramLabel(paramLabel)
    builder.build()

  private def setter(update: Object => Unit): ISetter =
    new ISetter:
      override def set[T](value: T): T =
        update(value.asInstanceOf[Object])
        value

  private def splitList(value: String): Vector[String] =
    value.split(",").iterator.map(_.trim).filter(_.nonEmpty).toVector

  private def withBooleanPresence(
    state: CliState,
    args:  Array[String],
    root:  CommandLine.ParseResult,
    sub:   Option[CommandLine.ParseResult],
  ): CliState =
    state.quiet = present(root, sub, "-q")
    state.verbose = verboseCount(args)
    state.forceColor = present(root, sub, "--force-color")
    state.noColor = present(root, sub, "--no-color")
    state.dryRun = root.hasMatchedOption("-n")
    state.exitOnFailure = root.hasMatchedOption("-x")
    state

  private def present(root: CommandLine.ParseResult, sub: Option[CommandLine.ParseResult], name: String): Boolean =
    root.hasMatchedOption(name) || sub.exists(_.hasMatchedOption(name))

  private def verboseCount(args: Array[String]): Int =
    args.iterator.map {
      case "--verbose" => 1
      case item if item.startsWith("-") && !item.startsWith("--") => item.count(_ == 'v')
      case _ => 0
    }.sum
