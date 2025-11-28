package me.davidgomesdev

import jakarta.enterprise.context.ApplicationScoped

fun interface Assistant {
    fun chat(userMessage: String): String
}

@ApplicationScoped
class Service(val assistant: Assistant) {

    fun query(input: String): String {
        println("> \uD83D\uDDE3\uFE0F User")
        println(input)

        println("> \uD83D\uDDA5\uFE0F System")

        val response = assistant.chat(input)

        println(response)

        return response
    }
}
