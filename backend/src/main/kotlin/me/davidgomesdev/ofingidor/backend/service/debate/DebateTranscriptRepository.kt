package me.davidgomesdev.ofingidor.backend.service.debate

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import me.davidgomesdev.ofingidor.shared.constants.DebateConstants
import me.davidgomesdev.ofingidor.shared.dto.ChatEvent
import me.davidgomesdev.ofingidor.shared.dto.Persona
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class DebateTranscriptRepository(private val objectMapper: ObjectMapper) {

    @Transactional
    fun appendUserPrompt(conversationId: UUID, turnIndex: Int, text: String) {
        DebateTurnEntity().also { entity ->
            entity.id = UuidCreator.getTimeOrderedEpoch()
            entity.conversationId = conversationId
            entity.turnIndex = turnIndex
            entity.entryType = DebateConstants.DEBATE_ENTRY_TYPE_USER_PROMPT
            entity.text = text
            entity.createdAt = OffsetDateTime.now()
        }.persist()
    }

    @Transactional
    fun appendPersonaTurn(
        conversationId: UUID,
        turnIndex: Int,
        speaker: Persona,
        text: String,
        sources: List<ChatEvent.Sources.Source>,
    ) {
        DebateTurnEntity().also { entity ->
            entity.id = UuidCreator.getTimeOrderedEpoch()
            entity.conversationId = conversationId
            entity.turnIndex = turnIndex
            entity.entryType = DebateConstants.DEBATE_ENTRY_TYPE_PERSONA_TURN
            entity.speakerPersonaId = speaker.codeName
            entity.text = text
            entity.sourcesJson = if (sources.isEmpty()) null else objectMapper.writeValueAsString(sources)
            entity.createdAt = OffsetDateTime.now()
        }.persist()
    }

    @Transactional
    fun loadTranscript(conversationId: UUID): List<DebateTurnEntity> =
        DebateTurnEntity.list("conversationId = ?1 order by createdAt asc, id asc", conversationId)
}
