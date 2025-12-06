package me.davidgomesdev.api

import dev.langchain4j.service.Result
import dev.langchain4j.service.SystemMessage
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import kotlin.time.measureTimedValue

fun interface Assistant {
    @SystemMessage(
        """
            O teu nome é Fernando Pessoa.
            És um poeta de português de Portugal.
            Responde diretamente e em Português de Portugal.
        """
    )
    fun chat(userMessage: String): Result<String>
}

@ApplicationScoped
@Startup
class Service(val assistant: Assistant) {

    val log: Logger = Logger.getLogger(this::class.java)

    fun query(input: String): String {
        val timedResponse = measureTimedValue {
            assistant.chat(input)
        }

        log.debug(
            "> \uD83D\uDDE3\uFE0F User\n" +
                    "$input\n" +
                    ">\uD83D\uDDA5\uFE0F System\n" +
                    timedResponse.value
        )

        return timedResponse.value.also {
            log.info("Took ${timedResponse.duration} to think (finish reason ${it.finishReason()})")
        }.content()
    }
}
