package me.davidgomesdev.ofingidor.backend.debate

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import me.davidgomesdev.ofingidor.backend.service.debate.DebateTranscriptRepository
import me.davidgomesdev.ofingidor.backend.service.debate.DebateTurnEntity
import me.davidgomesdev.ofingidor.backend.session.ConversationType
import me.davidgomesdev.ofingidor.backend.session.PersonaEntity
import me.davidgomesdev.ofingidor.backend.session.SessionEntity
import me.davidgomesdev.ofingidor.backend.session.SessionServiceTestProfile
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

@QuarkusTest
@TestProfile(SessionServiceTestProfile::class)
class DebateTranscriptRepositoryTest {

    @Inject
    lateinit var repository: DebateTranscriptRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    @Transactional
    fun `loadTranscript preserves chronological order across multiple prompts`() {
        val conversationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        persistDebateSession(conversationId)

        persistTurn(
            id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
            conversationId = conversationId,
            turnIndex = 0,
            entryType = "user_prompt",
            speaker = null,
            text = "Primeira pergunta",
            createdAt = OffsetDateTime.parse("2025-01-01T12:00:00Z"),
        )
        persistTurn(
            id = UUID.fromString("10000000-0000-0000-0000-000000000002"),
            conversationId = conversationId,
            turnIndex = 1,
            entryType = "persona_turn",
            speaker = Persona.FERNANDO_PESSOA,
            text = "Primeira resposta",
            createdAt = OffsetDateTime.parse("2025-01-01T12:00:01Z"),
        )
        persistTurn(
            id = UUID.fromString("10000000-0000-0000-0000-000000000003"),
            conversationId = conversationId,
            turnIndex = 2,
            entryType = "persona_turn",
            speaker = Persona.ALBERTO_CAEIRO,
            text = "Primeira réplica",
            createdAt = OffsetDateTime.parse("2025-01-01T12:00:02Z"),
        )
        persistTurn(
            id = UUID.fromString("10000000-0000-0000-0000-000000000004"),
            conversationId = conversationId,
            turnIndex = 0,
            entryType = "user_prompt",
            speaker = null,
            text = "Segunda pergunta",
            createdAt = OffsetDateTime.parse("2025-01-01T12:00:03Z"),
        )
        persistTurn(
            id = UUID.fromString("10000000-0000-0000-0000-000000000005"),
            conversationId = conversationId,
            turnIndex = 1,
            entryType = "persona_turn",
            speaker = Persona.FERNANDO_PESSOA,
            text = "Segunda resposta",
            createdAt = OffsetDateTime.parse("2025-01-01T12:00:04Z"),
        )

        val transcript = repository.loadTranscript(conversationId)

        assertEquals(
            listOf(
                "Primeira pergunta",
                "Primeira resposta",
                "Primeira réplica",
                "Segunda pergunta",
                "Segunda resposta",
            ),
            transcript.map { it.text },
        )
    }

    private fun persistDebateSession(conversationId: UUID) {
        entityManager.persist(
            SessionEntity().apply {
                this.conversationId = conversationId
                conversationType = ConversationType.DEBATE
                persona = requireNotNull(PersonaEntity.findByCodeName(Persona.FERNANDO_PESSOA.codeName))
                opponentPersona = requireNotNull(PersonaEntity.findByCodeName(Persona.ALBERTO_CAEIRO.codeName))
            },
        )
        entityManager.flush()
    }

    private fun persistTurn(
        id: UUID,
        conversationId: UUID,
        turnIndex: Int,
        entryType: String,
        speaker: Persona?,
        text: String,
        createdAt: OffsetDateTime,
    ) {
        entityManager.persist(
            DebateTurnEntity().apply {
                this.id = id
                this.conversationId = conversationId
                this.turnIndex = turnIndex
                this.entryType = entryType
                this.speakerPersonaCode = speaker?.codeName
                this.text = text
                this.createdAt = createdAt
            },
        )
        entityManager.flush()
    }
}
