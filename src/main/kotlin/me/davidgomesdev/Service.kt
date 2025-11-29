package me.davidgomesdev

import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

fun interface Assistant {
    fun chat(userMessage: String): String
}

@ApplicationScoped
@Startup
class Service(val assistant: Assistant) {

    val log: Logger = Logger.getLogger(this::class.java)

    fun query(input: String): String {
        log.info("> \uD83D\uDDE3\uFE0F User")
        log.info(input)

        log.info("> \uD83D\uDDA5\uFE0F System")

        val response = assistant.chat(input)

        log.info(response)

        return response
    }
}
