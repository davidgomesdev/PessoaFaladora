package me.davidgomesdev.ofingidor.backend.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import me.davidgomesdev.ofingidor.shared.dto.ChatEvent
import org.jboss.logging.Logger
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class ChatHistoryRepository(private val objectMapper: ObjectMapper) {

    private val log: Logger = Logger.getLogger(this::class.java)

    @Transactional
    fun persist(
        conversationId: UUID,
        userMessage: String,
        aiResponse: String,
        sources: List<ChatEvent.Sources.Source>,
        personaCode: String,
    ) {
        ChatHistoryEntity().also { entity ->
            entity.id = UuidCreator.getTimeOrderedEpoch()
            entity.conversationId = conversationId
            entity.userMessage = userMessage
            entity.aiResponse = aiResponse
            entity.sourcesJson = if (sources.isEmpty()) null else objectMapper.writeValueAsString(sources)
            entity.personaCode = personaCode
            entity.createdAt = OffsetDateTime.now()
        }.persist()
        log.debug("Persisted chat history for conversationId=$conversationId persona=$personaCode (${sources.size} sources)")
    }
}
