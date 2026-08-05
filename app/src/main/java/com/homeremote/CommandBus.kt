package com.homeremote

import java.util.concurrent.CopyOnWriteArrayList

data class Command(val action: String, val value: String)

object CommandBus {
    private val listeners = CopyOnWriteArrayList<(Command) -> Unit>()

    fun subscribe(listener: (Command) -> Unit) = listeners.add(listener)
    fun unsubscribe(listener: (Command) -> Unit) = listeners.remove(listener)

    fun post(command: Command) {
        listeners.forEach { it(command) }
    }
}
