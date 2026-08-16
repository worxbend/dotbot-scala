package io.worxbend.dotbot.core

object DirectiveRegistry:
  private val registeredHandlers: Vector[DirectiveHandler] =
    Vector(CreateHandler(), CleanHandler(), LinkHandler(), ShellHandler())

  val handlers: Map[Directive, DirectiveHandler] =
    registeredHandlers.map(handler => handler.directive -> handler).toMap
