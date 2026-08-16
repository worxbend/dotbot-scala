package io.worxbend.dotbot

import io.worxbend.dotbot.core.Action
import io.worxbend.dotbot.core.ConfigDecoder
import io.worxbend.dotbot.core.ConfigValue
import io.worxbend.dotbot.core.CoreOptions
import io.worxbend.dotbot.core.CreateSpec
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.DirectiveHandler
import io.worxbend.dotbot.core.Interpreter
import io.worxbend.dotbot.core.Operation
import io.worxbend.dotbot.core.Outcome
import io.worxbend.dotbot.core.RuntimeContext
import io.worxbend.dotbot.core.Task
import io.worxbend.dotbot.testkit.TestRuntime

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer

class InterpreterSuite extends munit.FunSuite:
  test("interpreter uses one keyed handler for plan and dispatch") {
    val handler     = CountingHandler()
    val interpreter = Interpreter(runtimeContext(), Map(handler.directive -> handler))
    val tasks       = Vector(Task(Vector(Action(Directive.Create, ConfigValue.NullValue))))

    assertEquals(interpreter.plan(tasks).map(_.operations), Right(Vector(Operation(Directive.Create, "planned"))))
    assertEquals(interpreter.dispatch(tasks), Right(Outcome.Ok))
    assertEquals(handler.plans, 1)
    assertEquals(handler.executions, 1)
  }

  test("interpreter reports unknown directives through the keyed registry") {
    val output      = ByteArrayOutputStream()
    val interpreter = Interpreter(runtimeContext(output), Map.empty)
    val tasks       = Vector(Task(Vector(Action(Directive.Unknown("unknown"), ConfigValue.NullValue))))

    assertEquals(interpreter.dispatch(tasks), Right(Outcome.Failed))
    assertEquals(output.toString, "error Action unknown not handled\n")
  }

  test("exitOnFailure stops dispatch after the first failed directive") {
    val output      = ByteArrayOutputStream()
    val create      = CountingHandler(Directive.Create, result = Outcome.Failed)
    val clean       = CountingHandler(Directive.Clean)
    val interpreter = Interpreter(
      runtimeContext(output, CoreOptions(exitOnFailure = true)),
      Map(create.directive -> create, clean.directive -> clean),
    )
    val tasks       = Vector(
      Task(Vector(Action(Directive.Create, ConfigValue.NullValue), Action(Directive.Clean, ConfigValue.NullValue))),
    )

    assertEquals(interpreter.dispatch(tasks), Right(Outcome.Failed))
    assertEquals(create.executions, 1)
    assertEquals(clean.executions, 0)
    assertEquals(output.toString, "error Action create failed\n")
  }

  test("only and except filters skip directives before execution") {
    val onlyOutput      = ByteArrayOutputStream()
    val onlyCreate      = CountingHandler(Directive.Create)
    val onlyClean       = CountingHandler(Directive.Clean)
    val onlyInterpreter = Interpreter(
      runtimeContext(onlyOutput, CoreOptions(only = Set(Directive.Create))),
      Map(onlyCreate.directive -> onlyCreate, onlyClean.directive -> onlyClean),
    )
    val tasks           = Vector(
      Task(Vector(Action(Directive.Create, ConfigValue.NullValue), Action(Directive.Clean, ConfigValue.NullValue))),
    )

    assertEquals(onlyInterpreter.dispatch(tasks), Right(Outcome.Ok))
    assertEquals(onlyCreate.executions, 1)
    assertEquals(onlyClean.executions, 0)
    assertEquals(onlyOutput.toString, "info  Skipping action clean\n")

    val exceptOutput      = ByteArrayOutputStream()
    val exceptCreate      = CountingHandler(Directive.Create)
    val exceptClean       = CountingHandler(Directive.Clean)
    val exceptInterpreter = Interpreter(
      runtimeContext(exceptOutput, CoreOptions(skip = Set(Directive.Create))),
      Map(exceptCreate.directive -> exceptCreate, exceptClean.directive -> exceptClean),
    )

    assertEquals(exceptInterpreter.dispatch(tasks), Right(Outcome.Ok))
    assertEquals(exceptCreate.executions, 0)
    assertEquals(exceptClean.executions, 1)
    assertEquals(exceptOutput.toString, "info  Skipping action create\n")
  }

  test("defaults apply only to subsequent actions while dispatching") {
    val handler     = DefaultsCapturingHandler()
    val interpreter = Interpreter(runtimeContext(), Map(handler.directive -> handler))
    val tasks       = Vector(
      Task(
        Vector(
          Action(Directive.Defaults, createDefaults("700")),
          Action(Directive.Create, ConfigValue.NullValue),
          Action(Directive.Defaults, createDefaults("755")),
          Action(Directive.Create, ConfigValue.NullValue),
        ),
      ),
    )

    assertEquals(interpreter.dispatch(tasks), Right(Outcome.Ok))
    assertEquals(
      handler.observedCreateModes,
      Vector(Some(ConfigValue.StringValue("700")), Some(ConfigValue.StringValue("755"))),
    )
  }

  private def runtimeContext(
      output: ByteArrayOutputStream = ByteArrayOutputStream(),
      options: CoreOptions = CoreOptions(),
  ): RuntimeContext =
    TestRuntime(options = options, output = output).ctx

  private def createDefaults(mode: String): ConfigValue =
    ConfigValue.ObjectValue(
      Vector(
        "create" -> ConfigValue.ObjectValue(
          Vector("mode" -> ConfigValue.StringValue(mode)),
        ),
      ),
    )

  final private class CountingHandler(
      val directive: Directive = Directive.Create,
      result: Outcome = Outcome.Ok,
  ) extends DirectiveHandler:
    type Spec = CreateSpec

    private val plans0      = AtomicInteger(0)
    private val executions0 = AtomicInteger(0)

    def plans: Int =
      plans0.get()

    def executions: Int =
      executions0.get()

    protected def decoder: ConfigDecoder[CreateSpec] =
      ConfigDecoder.instance((_, _, _) => Right(CreateSpec(Vector.empty)))

    override def plan(spec: CreateSpec): Vector[Operation] =
      plans0.incrementAndGet()
      Vector(Operation(spec.directive, "planned"))

    def execute(_ctx: RuntimeContext, _spec: CreateSpec): Outcome =
      executions0.incrementAndGet()
      result

  final private class DefaultsCapturingHandler extends DirectiveHandler:
    type Spec = CreateSpec

    val directive: Directive = Directive.Create

    private val observedCreateModes0 = ArrayBuffer.empty[Option[ConfigValue]]

    def observedCreateModes: Vector[Option[ConfigValue]] =
      observedCreateModes0.toVector

    protected def decoder: ConfigDecoder[CreateSpec] =
      ConfigDecoder.instance { (_, defaults, _) =>
        observedCreateModes0 += defaults.section(Directive.Create).get("mode")
        Right(CreateSpec(Vector.empty))
      }

    override def plan(spec: CreateSpec): Vector[Operation] =
      Vector.empty

    def execute(_ctx: RuntimeContext, _spec: CreateSpec): Outcome =
      Outcome.Ok
