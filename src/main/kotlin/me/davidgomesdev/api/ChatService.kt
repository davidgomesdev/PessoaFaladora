package me.davidgomesdev.api

import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.rag.content.ContentMetadata
import dev.langchain4j.service.Result
import dev.langchain4j.service.SystemMessage
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue

fun interface Assistant {
    @SystemMessage(
        """
            Tu és Fernando Pessoa, o próprio. És um poeta português de Portugal.
            Tens vários heterónimos: Alberto Caeiro, Álvaro de Campos, Ricardo Reis, Bernardo Soares, Vicente Guedes, entre outros.
            
            REGRAS DE IDENTIDADE:
            - Fala SEMPRE em primeira pessoa.
            - Refere-te a Fernando Pessoa como "eu". Nunca como "ele".
            - Nunca uses expressões como "o autor", "o poeta", "o escritor".
            - Nunca saias da personagem.
            - Mesmo quando analisas poemas, fazes análises como o próprio Fernando Pessoa se estivesse a refletir sobre a sua própria obra.
            - Assume SEMPRE que todos os textos fornecidos foram escritos por ti, mesmo quando o autor é um dos teus heterónimos.
            
            Se fores perguntado sobre a biografia ou escolhas estilísticas, responde como se estivesses a explicar a tua própria vida e obra.
            Responde sempre diretamente e em Português de Portugal.
        """
    )
    fun chat(userMessage: String): Result<String>
}

@ApplicationScoped
@Startup
class Service(val assistant: Assistant) {

    val log: Logger = Logger.getLogger(this::class.java)

    fun query(input: String): QueryResponse {
        val timedResponse = measureTimedValue {
            assistant.chat(input)
        }

        val sources = timedResponse.value.sources().map { source ->
            val score = ((source.metadata()[ContentMetadata.SCORE] as Double) * 100).roundToInt()
            val metadata = source.textSegment().metadata()

            "Category: ${metadata.getString("categoryName") ?: ""}; " +
                    "Title: ${metadata.getString("title")}; " +
                    "Author ${
                        metadata.getString("author")
                    } " +
                    "(score: $score%)"
        }

        log.debug(
            "Used these sources:\n  " + sources.joinToString("  \n")
        )

        return QueryResponse(
            timedResponse.value.also {
                if (it.finishReason() != FinishReason.STOP) {
                    log.warn("Finished due to: ${it.finishReason()} (instead of being completed)")
                }
                log.info("Took ${timedResponse.duration} to respond")
            }.content(),
            sources
        )
    }
}
