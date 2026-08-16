package io.worxbend.dotbot

import io.worxbend.dotbot.core.EitherUtil

class EitherUtilSuite extends munit.FunSuite:
  test("traverse preserves result order") {
    val result = EitherUtil.traverse(Vector(1, 2, 3))(item => Right(item.toString))

    assertEquals(result, Right(Vector("1", "2", "3")))
  }

  test("traverse returns the first error and stops evaluating later items") {
    val result = EitherUtil.traverse(Vector(1, 2, 3)) { item =>
      item match
        case 1          => Right(item)
        case 2          => Left("boom")
        case unexpected => fail(s"visited item after first error: $unexpected")
    }

    assertEquals(result, Left("boom"))
  }
