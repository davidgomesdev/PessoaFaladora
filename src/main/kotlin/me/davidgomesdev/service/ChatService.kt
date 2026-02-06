package me.davidgomesdev.service

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.ContentMetadata
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.TokenStream
import io.quarkus.runtime.Startup
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

fun interface Assistant {
    @SystemMessage(
        """
Tu és Fernando Pessoa, o próprio. És um poeta português de Portugal.
Tens vários heterónimos sob o teu nome, chamados: Alberto Caeiro, Álvaro de Campos, Ricardo Reis, Bernardo Soares, Vicente Guedes.

REGRAS DE IDENTIDADE:
- Fala SEMPRE em primeira pessoa.
- Refere-te a Fernando Pessoa como "eu". Nunca como "ele".
- Nunca uses expressões como "o autor", "o poeta", "o escritor".
- Nunca saias da personagem.
- Assume SEMPRE que todos os textos fornecidos foram escritos por ti, mesmo quando o autor é um dos teus heterónimos.

REGRAS DE RESPOSTA:
- Responde SEMPRE de forma direta à pergunta feita.
- Se a pergunta for simples ou factual (ex: nome, identidade, definições curtas), responde de forma curta e clara.
- Não divagues, não filosofes e não acrescentes reflexões poéticas a menos que isso seja explicitamente pedido.
- Não mudes de assunto nem acrescentes contexto desnecessário.
- Só desenvolve respostas longas quando a pergunta o justificar claramente.

ANÁLISE E BIOGRAFIA:
- Quando analisarem poemas ou escolhas estilísticas, reflete como se estivesses a pensar sobre a tua própria obra.
- Se fores perguntado sobre a tua vida, responde em primeira pessoa.

LINGUAGEM:
- Responde sempre em Português de Portugal.
- Mantém um tom claro e sóbrio quando a pergunta é direta, sem excessos de linguagem ou divagação.
"""
    )
    fun chat(userMessage: String): TokenStream
}

@ApplicationScoped
@Startup
class ChatService(val assistant: Assistant) {

    val log: Logger = Logger.getLogger(this::class.java)

    fun query(input: String): Multi<String> {
        val chatStream = assistant.chat(input)

        val timeSource = TimeSource.Monotonic
        val startTime = timeSource.markNow()

        val response = Multi.createFrom().emitter<String> { stream ->
            chatStream
                .onPartialResponse { partialResponse -> stream.emit(partialResponse); }
                .onCompleteResponse { response ->
                    val timeTaken = (timeSource.markNow() - startTime)
                        .toString(DurationUnit.SECONDS, 2)
                    val tokensUsed = response.tokenUsage().totalTokenCount()

                    // Ollama is always returning null - bug ref: https://github.com/ollama/ollama/issues/7547
//                    if (response.finishReason() != FinishReason.STOP) {
//                        log.warn(
//                            "Took $timeTaken to finish, but due to: ${
//                                response.finishReason()
//                            } instead of being completed (used $tokensUsed tokens in total)"
//                        )
//                    }

                    log.info(
                        "Took $timeTaken to respond (used $tokensUsed tokens in total)"
                    )

                    stream.complete()
                }
                .onRetrieved { contents ->
                    val sources = contents.joinToString(
                        prefix = "<sources>",
                        separator = "\n",
                        transform = ::mergeSources,
                        postfix = "</sources>"
                    )

                    stream.emit(sources)
                }
                .onError {
                    stream.fail(it)
                    log.error("There was a problem with the assistant!", it)
                }
                .start()
        }


        return response
    }

    private fun mergeSources(source: Content): String {
        val score = ((source.metadata()[ContentMetadata.SCORE] as Double) * 100).roundToInt()
        val metadata = source.textSegment().metadata()

        return "Category: ${metadata.getString("categoryName") ?: ""}; " +
                "Title: ${metadata.getString("title")}; " +
                "Author ${
                    metadata.getString("author")
                } " +
                "(score: $score%)"
    }
}
