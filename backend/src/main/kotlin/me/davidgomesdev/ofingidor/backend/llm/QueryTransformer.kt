package me.davidgomesdev.ofingidor.backend.llm

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer
import dev.langchain4j.rag.query.transformer.QueryTransformer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import me.davidgomesdev.ofingidor.backend.llm.config.RAGConfig
import org.jboss.logging.Logger

@ApplicationScoped
class QueryTransformerProducer(val config: RAGConfig) {
    val log: Logger = Logger.getLogger(this::class.java)

    @Singleton
    @Suppress("unused")
    fun queryTransformer(chatModel: ChatModel): QueryTransformer {
        if (!config.expandQuery()) {
            log.info("Using simple query transformer")
            return DefaultQueryTransformer()
        }

        return ExpandingQueryTransformer(chatModel, PromptTemplate.from(config.expandingQueryTemplate()))
    }
}
