package me.davidgomesdev.ofingidor.backend.llm.rag

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.injector.DefaultContentInjector
import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.ofingidor.backend.observability.attributes
import me.davidgomesdev.ofingidor.backend.observability.span
import org.jboss.logging.Logger
import java.io.File

private const val PROMPT_FILE_NAME = "content_injector.txt"

@ApplicationScoped
class TextsContentInjector : DefaultContentInjector(
    mutableListOf(TextAttributes.AUTHOR, TextAttributes.TITLE, TextAttributes.CATEGORY_NAME)
) {

    // Used if there is no local
    private val promptTemplate: String =
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("prompts/$PROMPT_FILE_NAME")!!
            .reader().readText()

    val log: Logger = Logger.getLogger(TextsContentInjector::class.java)

    override fun inject(contents: List<Content>, chatMessage: ChatMessage): ChatMessage {
        return super.inject(contents, chatMessage).also {
            if (chatMessage !is UserMessage) {
                log.warn("No tracing in place for non-user messages!")
                return@also
            }

            if (contents.isEmpty()) {
                span().addEvent("No content injected")
                return@also
            }

            span().addEvent("Content injected", attributes {
                put(
                    "message_with_content",
                    (it as UserMessage).contents()
                        .filterIsInstance<TextContent>()
                        .joinToString("\n\n", transform = TextContent::text)
                )
            })
        }
    }

    override fun createPrompt(chatMessage: ChatMessage, contents: MutableList<Content?>): Prompt {
        val variables: MutableMap<String, Any> = hashMapOf(
            "userMessage" to (chatMessage as UserMessage).singleText(),
            "contents" to format(contents)
        )
        val localPromptFile = File(PROMPT_FILE_NAME)
        val prompt = PromptTemplate.from(
            if (localPromptFile.exists()) {
                localPromptFile.readText()
            } else promptTemplate
        )

        return prompt.apply(variables)
    }

    override fun format(content: Content): String {
        val segment = content.textSegment()

        val segmentContent = segment.text()
        val segmentMetadata = segment.metadata()

        val authorDescription = segmentMetadata.getString(TextAttributes.AUTHOR)
            .let { author -> if (author == "Fernando Pessoa") "em teu nome" else "sob o teu heterónimo $author" }

        return "- Texto '${segmentMetadata.getString(TextAttributes.TITLE)}' " +
                "da coleção '${segmentMetadata.getString(TextAttributes.CATEGORY_NAME)}' " +
                "$authorDescription:\n" +
                segmentContent
    }
}
