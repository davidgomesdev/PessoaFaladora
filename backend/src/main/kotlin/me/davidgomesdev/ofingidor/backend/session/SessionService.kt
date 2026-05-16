package me.davidgomesdev.ofingidor.backend.session

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.f4b6a3.uuid.UuidCreator
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.jboss.logging.Logger
import java.time.Instant
import java.util.Date
import java.util.UUID

@ApplicationScoped
@Startup
class SessionService(private val config: SessionConfig) {

    private val log: Logger = Logger.getLogger(this::class.java)

    private val signer: MACSigner by lazy {
        MACSigner(config.jwt().secret().toByteArray(Charsets.UTF_8))
    }

    private val verifier: MACVerifier by lazy {
        MACVerifier(config.jwt().secret().toByteArray(Charsets.UTF_8))
    }

    @PostConstruct
    fun validateConfig() {
        val secretBytes = config.jwt().secret().toByteArray(Charsets.UTF_8)
        require(secretBytes.size >= 32) {
            "session.jwt.secret must be at least 32 bytes for HS256 (got ${secretBytes.size})"
        }
        require(config.memory().maxMessages() > 0) {
            "session.memory.max-messages must be greater than 0 (got ${config.memory().maxMessages()})"
        }
        log.info(
            "SessionService initialized (JWT TTL: ${config.jwt().ttl()}, memory max: ${
                config.memory().maxMessages()
            })"
        )
    }

    @Transactional
    fun createSession(persona: Persona): ConversationSession {
        val personaEntity = PersonaEntity.findByCodeName(persona.codeName)
            ?: error("Persona '${persona.codeName}' not found in database")
        val conversationId = UuidCreator.getTimeOrderedEpoch()
        val session = SessionEntity()

        session.conversationId = conversationId
        session.conversationType = ConversationType.SINGLE
        session.persona = personaEntity
        session.opponentPersona = null

        session.persist()

        log.info("New session created: conversationId=$conversationId persona=${persona.codeName}")

        val token = buildJwt(conversationId.toString())

        return ConversationSession(token, conversationId.toString())
    }

    @Transactional
    fun createDebateSession(personaA: Persona, personaB: Persona): ConversationSession {
        require(personaA != personaB) { "debate personas must be different" }

        val personaEntity = PersonaEntity.findByCodeName(personaA.codeName)
            ?: error("Persona '${personaA.codeName}' not found in database")
        val opponentEntity = PersonaEntity.findByCodeName(personaB.codeName)
            ?: error("Persona '${personaB.codeName}' not found in database")

        val conversationId = UuidCreator.getTimeOrderedEpoch()

        SessionEntity().also { session ->
            session.conversationId = conversationId
            session.conversationType = ConversationType.DEBATE
            session.persona = personaEntity
            session.opponentPersona = opponentEntity
            session.persist()
        }

        return ConversationSession(buildJwt(conversationId.toString()), conversationId.toString())
    }

    fun extractConversationId(token: String): Either<SessionError, String> {
        return try {
            val jwt = SignedJWT.parse(token)
            if (!jwt.verify(verifier)) {
                log.warn("JWT rejected: invalid signature")
                return SessionError.INVALID_TOKEN.left()
            }
            val expiry = jwt.jwtClaimsSet.expirationTime
            if (expiry != null && expiry.before(Date())) {
                log.warn("JWT rejected: token expired at $expiry")
                return SessionError.INVALID_TOKEN.left()
            }
            val conversationId = jwt.jwtClaimsSet.getStringClaim("conversationId")
                ?: return SessionError.INVALID_TOKEN.left()
            conversationId.right()
        } catch (e: Exception) {
            log.warn("JWT rejected: malformed token (${e.message})")
            SessionError.INVALID_TOKEN.left()
        }
    }

    @Transactional
    fun getPersona(conversationId: String): Either<SessionError, Persona> {
        val uuid = try {
            UUID.fromString(conversationId)
        } catch (_: IllegalArgumentException) {
            log.warn("Session lookup failed: invalid UUID format '$conversationId'")
            return SessionError.SESSION_NOT_FOUND.left()
        }
        val session = SessionEntity.findById(uuid)
            ?: run {
                log.warn("Session not found: conversationId=$conversationId")
                return SessionError.SESSION_NOT_FOUND.left()
            }
        val persona = Persona.entries.firstOrNull { it.codeName == session.persona.codeName }
            ?: error("Unknown persona code '${session.persona.codeName}' in database")
        return persona.right()
    }

    @Transactional
    fun getConversationParticipants(conversationId: String): Either<SessionError, ConversationParticipants> {
        val uuid = try {
            UUID.fromString(conversationId)
        } catch (_: IllegalArgumentException) {
            return SessionError.SESSION_NOT_FOUND.left()
        }

        val session = SessionEntity.findById(uuid) ?: return SessionError.SESSION_NOT_FOUND.left()

        val primary = resolvePersona(session.persona.codeName) ?: return SessionError.PERSONA_MISMATCH.left()

        return when (session.conversationType) {
            ConversationType.SINGLE -> {
                if (session.opponentPersona != null) {
                    SessionError.SESSION_MODE_MISMATCH.left()
                } else {
                    ConversationParticipants(
                        type = ConversationType.SINGLE,
                        persona = primary,
                    ).right()
                }
            }

            ConversationType.DEBATE -> {
                val opponent = session.opponentPersona ?: return SessionError.SESSION_MODE_MISMATCH.left()
                val resolvedOpponent = resolvePersona(opponent.codeName)
                    ?: return SessionError.PERSONA_PAIR_MISMATCH.left()

                if (resolvedOpponent == primary) {
                    SessionError.PERSONA_PAIR_MISMATCH.left()
                } else {
                    ConversationParticipants(
                        type = ConversationType.DEBATE,
                        persona = primary,
                        opponentPersona = resolvedOpponent,
                    ).right()
                }
            }
        }
    }

    private fun resolvePersona(codeName: String): Persona? =
        Persona.entries.firstOrNull { it.codeName == codeName }

    private fun buildJwt(conversationId: String): String {
        val now = Instant.now()
        val expiry = now.plus(config.jwt().ttl())
        val claims = JWTClaimsSet.Builder()
            .claim("conversationId", conversationId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiry))
            .build()
        val header = JWSHeader(JWSAlgorithm.HS256)
        val jwt = SignedJWT(header, claims)
        jwt.sign(signer)
        return jwt.serialize()
    }
}
