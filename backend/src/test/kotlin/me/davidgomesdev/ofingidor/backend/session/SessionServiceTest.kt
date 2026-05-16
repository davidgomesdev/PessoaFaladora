package me.davidgomesdev.ofingidor.backend.session

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(SessionServiceTestProfile::class)
class SessionServiceTest {

    @Inject
    lateinit var sessionService: SessionService

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `createSession returns a valid JWT containing conversationId`() {
        val session = sessionService.createSession(Persona.ALBERTO_CAEIRO)
        assertNotNull(session.token)
        assertNotNull(session.conversationId)
        assertTrue(session.conversationId.isNotBlank())
    }

    @Test
    fun `createSession participants metadata is single with no opponent`() {
        val session = sessionService.createSession(Persona.ALBERTO_CAEIRO)
        val metadata = sessionService.getConversationParticipants(session.conversationId)

        assertTrue(metadata.isRight())
        assertEquals(ConversationType.SINGLE, metadata.getOrNull()!!.type)
        assertEquals(Persona.ALBERTO_CAEIRO, metadata.getOrNull()!!.persona)
        assertNull(metadata.getOrNull()!!.opponentPersona)
    }

    @Test
    fun `extractConversationId returns Left INVALID_TOKEN for invalid token`() {
        val result = sessionService.extractConversationId("not.a.valid.jwt")
        assertTrue(result.isLeft())
        assertEquals(SessionError.INVALID_TOKEN, result.leftOrNull())
    }

    @Test
    fun `extractConversationId returns Left INVALID_TOKEN for empty token`() {
        val result = sessionService.extractConversationId("")
        assertTrue(result.isLeft())
        assertEquals(SessionError.INVALID_TOKEN, result.leftOrNull())
    }

    @Test
    fun `extractConversationId returns Right with conversationId for valid token`() {
        val session = sessionService.createSession(Persona.ALBERTO_CAEIRO)
        val result = sessionService.extractConversationId(session.token)
        assertTrue(result.isRight())
        assertEquals(session.conversationId, result.getOrNull())
    }

    @Test
    fun `getPersona returns Right with persona stored during createSession`() {
        val session = sessionService.createSession(Persona.RICARDO_REIS)
        val result = sessionService.getPersona(session.conversationId)
        assertTrue(result.isRight())
        assertEquals(Persona.RICARDO_REIS, result.getOrNull())
    }

    @Test
    fun `getPersona returns Left SESSION_NOT_FOUND for unknown conversationId`() {
        val result = sessionService.getPersona("00000000-0000-0000-0000-000000000000")
        assertTrue(result.isLeft())
        assertEquals(SessionError.SESSION_NOT_FOUND, result.leftOrNull())
    }

    @Test
    fun `createDebateSession stores both personas`() {
        val session = sessionService.createDebateSession(Persona.FERNANDO_PESSOA, Persona.ALBERTO_CAEIRO)
        val metadata = sessionService.getConversationParticipants(session.conversationId)

        assertTrue(metadata.isRight())
        assertEquals(ConversationType.DEBATE, metadata.getOrNull()!!.type)
        assertEquals(Persona.FERNANDO_PESSOA, metadata.getOrNull()!!.persona)
        assertEquals(Persona.ALBERTO_CAEIRO, metadata.getOrNull()!!.opponentPersona)
    }

    @Test
    fun `createDebateSession rejects duplicate personas`() {
        val error = runCatching {
            sessionService.createDebateSession(Persona.RICARDO_REIS, Persona.RICARDO_REIS)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    @Transactional
    fun `getConversationParticipants returns SESSION_MODE_MISMATCH for debate session without opponent`() {
        persistSession(
            conversationId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            type = ConversationType.DEBATE,
            persona = Persona.FERNANDO_PESSOA,
            opponent = null,
        )

        val result = sessionService.getConversationParticipants("11111111-1111-1111-1111-111111111111")

        assertTrue(result.isLeft())
        assertEquals(SessionError.SESSION_MODE_MISMATCH, result.leftOrNull())
    }

    @Test
    @Transactional
    fun `getConversationParticipants returns PERSONA_PAIR_MISMATCH for duplicate debate personas`() {
        persistSession(
            conversationId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            type = ConversationType.DEBATE,
            persona = Persona.FERNANDO_PESSOA,
            opponent = Persona.FERNANDO_PESSOA,
        )

        val result = sessionService.getConversationParticipants("22222222-2222-2222-2222-222222222222")

        assertTrue(result.isLeft())
        assertEquals(SessionError.PERSONA_PAIR_MISMATCH, result.leftOrNull())
    }

    private fun persistSession(
        conversationId: UUID,
        type: ConversationType,
        persona: Persona,
        opponent: Persona?,
    ) {
        val session = SessionEntity().apply {
            this.conversationId = conversationId
            this.conversationType = type
            this.persona = requireNotNull(PersonaEntity.findByCodeName(persona.codeName))
            this.opponentPersona = opponent?.let { requireNotNull(PersonaEntity.findByCodeName(it.codeName)) }
        }
        entityManager.persist(session)
        entityManager.flush()
    }
}
