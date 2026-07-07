package io.worxbend.dotbot.config

import io.worxbend.dotbot.core.Action as CoreAction
import io.worxbend.dotbot.core.ConfigValue as CoreConfigValue
import io.worxbend.dotbot.core.Task as CoreTask

type ConfigValue = CoreConfigValue
val ConfigValue: CoreConfigValue.type = CoreConfigValue

type Action = CoreAction
val Action: CoreAction.type = CoreAction

type Task = CoreTask
val Task: CoreTask.type = CoreTask
