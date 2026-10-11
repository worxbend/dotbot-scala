package io.worxbend.dotbot.core

import scala.annotation.tailrec

object EitherUtil:
  def traverse[E, A, B](items: IterableOnce[A])(f: A => Either[E, B]): Either[E, Vector[B]] =
    val iterator = items.iterator

    @tailrec
    def loop(values: List[B]): Either[E, Vector[B]] =
      if !iterator.hasNext then Right(values.reverse.toVector)
      else
        f(iterator.next()) match
          case Left(error)  => Left(error)
          case Right(value) => loop(value :: values)

    loop(Nil)
