package me.davidgomesdev.llm

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.injector.DefaultContentInjector
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class TextsContentInjector : DefaultContentInjector(
    CONTENT_INJECTOR_TEMPLATE,
    mutableListOf("author", "title", "categoryName")
) {
    override fun format(content: Content): String {
        val segment = content.textSegment()

        val segmentContent = segment.text()
        val segmentMetadata = segment.metadata()

        val authorDescription = segmentMetadata.getString("author")
            .let { author -> if (author == "Fernando Pessoa") "em teu nome" else "sob o teu heterónimo $author" }

        return "Texto " +
                "'${segmentMetadata.getString("title")}' da coleção '${segmentMetadata.getString("categoryName")}' " +
                "$authorDescription:\n" +
                segmentContent
    }
}
