package me.davidgomesdev.ofingidor.backend.llm

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chat_history")
class ChatHistoryEntity : PanacheEntityBase {

    @Id
    lateinit var id: UUID

    @Column(name = "conversation_id", nullable = false)
    lateinit var conversationId: UUID

    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    lateinit var userMessage: String

    @Column(name = "ai_response", nullable = false, columnDefinition = "TEXT")
    lateinit var aiResponse: String

    @Column(name = "sources_json", columnDefinition = "TEXT")
    var sourcesJson: String? = null

    @Column(name = "persona_id", nullable = false, length = 50)
    lateinit var personaId: String

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime

    companion object : PanacheCompanionBase<ChatHistoryEntity, UUID>
}
