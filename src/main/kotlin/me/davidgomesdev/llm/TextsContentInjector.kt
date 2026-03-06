package me.davidgomesdev.llm

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.injector.DefaultContentInjector
import io.opentelemetry.api.trace.Span
import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.observability.attributes
import org.jboss.logging.Logger

val CONTENT_INJECTOR_TEMPLATE: PromptTemplate = PromptTemplate.from(
    """
    {{userMessage}}
    
    Responde tendo em conta estes textos teus:
    
    {{contents}}
    """.trimIndent()
)

@ApplicationScoped
class TextsContentInjector : DefaultContentInjector(
    CONTENT_INJECTOR_TEMPLATE,
    mutableListOf("author", "title", "categoryName")
) {
    val log: Logger = Logger.getLogger(TextsContentInjector::class.java)

    override fun inject(contents: List<Content>, chatMessage: ChatMessage): ChatMessage {
        return super.inject(contents, chatMessage).also {
            if (chatMessage !is UserMessage) {
                log.warn("No tracing in place for non-user messages!")
                return@also
            }

            Span.current().addEvent("Content injected", attributes {
                put(
                    "message_with_content",
                    (it as UserMessage).contents()
                        .filterIsInstance<TextContent>()
                        .joinToString("\n\n", transform = TextContent::text)
                )
            })
        }
    }

    override fun format(content: Content): String {
        val segment = content.textSegment()

        val segmentContent = segment.text()
        val segmentMetadata = segment.metadata()

        val authorDescription = segmentMetadata.getString("author")
            .let { author -> if (author == "Fernando Pessoa") "em teu nome" else "sob o teu heterónimo $author" }

        return "- Texto da coleção '${segmentMetadata.getString("categoryName")}' " +
                "$authorDescription:\n" +
                segmentContent
    }
}
