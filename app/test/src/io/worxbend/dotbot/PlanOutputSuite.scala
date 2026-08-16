package io.worxbend.dotbot

import io.worxbend.dotbot.app.PlanOutput
import io.worxbend.dotbot.core.DetailStyle
import io.worxbend.dotbot.core.Directive
import io.worxbend.dotbot.core.Operation
import io.worxbend.dotbot.core.Plan

class PlanOutputSuite extends munit.FunSuite:
  private val threeOperations = Plan(
    Vector(
      Operation(Directive.Create, "generated"),
      Operation(Directive.Link, "linked.txt", "source.txt", DetailStyle.LinkTarget),
      Operation(Directive.Shell, "echo ok", "Echo", DetailStyle.ShellDescription),
    ),
  )

  test("text output preserves header and operation detail styles") {
    assertEquals(
      PlanOutput.text(threeOperations, taskCount = 3, configFileCount = 1, base = "/tmp/home"),
      """Plan: 3 operation(s), 3 task(s), 1 config file(s), base /tmp/home
        |create  generated
        |link    linked.txt -> source.txt
        |shell   echo ok [Echo]
        |""".stripMargin,
    )
  }

  test("targets start in the same column for every directive") {
    val lines         = PlanOutput.text(threeOperations, 3, 1, "/tmp/home").linesIterator.drop(1).toVector
    val targetColumns = lines.map(line => line.indexOf(line.trim.split(" +")(1)))

    // The column is what makes a plan scannable; it used to print the literal text "%-7s".
    assertEquals(targetColumns.distinct.size, 1, lines)
    assert(!lines.exists(_.contains("%")), lines)
  }

  test("an unknown directive keeps its raw name and still gets a separator") {
    val plan = Plan(Vector(Operation(Directive.Unknown("a-very-long-directive"), "target")))

    assertEquals(
      PlanOutput.text(plan, 1, 1, "/tmp/home"),
      """Plan: 1 operation(s), 1 task(s), 1 config file(s), base /tmp/home
        |a-very-long-directive target
        |""".stripMargin,
    )
  }

  test("an operation with no detail renders no suffix") {
    val plan = Plan(Vector(Operation(Directive.Create, "generated", "", DetailStyle.Parenthesized)))

    assert(PlanOutput.text(plan, 1, 1, "/tmp").linesIterator.toVector.last.endsWith("generated"))
  }

  test("each detail style has its own bracket") {
    assertEquals(DetailStyle.Parenthesized.suffix("d"), " (d)")
    assertEquals(DetailStyle.LinkTarget.suffix("d"), " -> d")
    assertEquals(DetailStyle.ShellDescription.suffix("d"), " [d]")
  }

  test("an empty plan renders only its header") {
    assertEquals(
      PlanOutput.text(Plan(), taskCount = 0, configFileCount = 1, base = "/tmp/home"),
      "Plan: 0 operation(s), 0 task(s), 1 config file(s), base /tmp/home\n",
    )
  }

  /**
   * Terminal columns occupied by a string, stated independently of the renderer so that this is a
   * specification of the expected layout rather than a restatement of the code under test.
   */
  private def terminalColumns(text: String): Int =
    text
      .codePoints()
      .toArray
      .toVector
      .map {
        case 0xfe0f | 0x200d         => 0 // variation selector and zero-width joiner draw nothing
        case wide if wide >= 0x1f300 => 2 // emoji occupy two columns
        case _                       => 1
      }
      .sum

  test("stylish output draws a box whose borders all line up") {
    val rendered = PlanOutput.text(threeOperations, 3, 1, "/tmp/home", stylish = true)
    val lines    = rendered.linesIterator.toVector
    val box      = lines.filter(line => line.startsWith("╭") || line.startsWith("│") || line.startsWith("╰"))

    // Measured in UTF-16 units an emoji counts as three, so the right-hand border used to sit a
    // column or two off on exactly the lines that had one.
    assertEquals(box.map(terminalColumns).distinct.size, 1, box)
    assert(lines.head.startsWith("╭"), lines)
    assert(box.last.startsWith("╰"), box)
    assert(box.tail.init.forall(_.endsWith(" │")), box)
  }

  test("a long base directory widens the whole box, not just its own line") {
    val rendered = PlanOutput.text(threeOperations, 3, 1, "/a/very/long/base/directory/path", stylish = true)
    val box      = rendered.linesIterator.toVector.filter(line =>
      line.startsWith("╭") || line.startsWith("│") || line.startsWith("╰"),
    )

    assertEquals(box.map(terminalColumns).distinct.size, 1, box)
  }

  test("stylish output still lists every operation") {
    val rendered = PlanOutput.text(threeOperations, 3, 1, "/tmp/home", stylish = true)

    assert(rendered.contains("linked.txt -> source.txt"), rendered)
    assert(rendered.contains("echo ok [Echo]"), rendered)
  }

  test("json output preserves key order and trailing newline") {
    val plan = Plan(Vector(Operation(Directive.Link, "linked.txt", "source.txt", DetailStyle.LinkTarget)))

    assertEquals(
      PlanOutput.json(plan, taskCount = 1, configFileCount = 1, base = "/tmp/home"),
      """{
        |  "task_count": 1,
        |  "config_file_count": 1,
        |  "operation_count": 1,
        |  "base": "/tmp/home",
        |  "operations": [
        |    {
        |      "directive": "link",
        |      "target": "linked.txt",
        |      "detail": "source.txt"
        |    }
        |  ]
        |}
        |""".stripMargin,
    )
  }

  test("json output is valid json even when a path contains characters that need escaping") {
    val plan = Plan(Vector(Operation(Directive.Link, """a"b\c""", "source", DetailStyle.LinkTarget)))

    val parsed = ujson.read(PlanOutput.json(plan, 1, 1, "/tmp/home"))
    assertEquals(parsed("operations")(0)("target").str, """a"b\c""")
  }

  test("an empty json plan still carries its counts") {
    val parsed = ujson.read(PlanOutput.json(Plan(), taskCount = 0, configFileCount = 2, base = "/tmp"))

    assertEquals(parsed("operation_count").num, 0d)
    assertEquals(parsed("config_file_count").num, 2d)
    assertEquals(parsed("operations").arr.length, 0)
  }
