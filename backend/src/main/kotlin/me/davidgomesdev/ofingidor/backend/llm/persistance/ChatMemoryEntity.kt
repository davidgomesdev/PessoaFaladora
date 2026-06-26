package me.davidgomesdev.ofingidor.backend.llm.persistance

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chat_memory")
class ChatMemoryEntity : PanacheEntityBase {

    @Id
    lateinit var id: UUID

    @Column(name = "conversation_id", nullable = false)
    lateinit var conversationId: UUID

    @Column(name = "message_json", nullable = false, columnDefinition = "TEXT")
    lateinit var messageJson: String

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime

    companion object : PanacheCompanionBase<ChatMemoryEntity, UUID> {
        fun findByConversationIdOrdered(conversationId: UUID): List<ChatMemoryEntity> =
            find(
                "FROM ChatMemoryEntity WHERE conversationId = :cid ORDER BY createdAt ASC",
                mapOf("cid" to conversationId)
            ).list()

        fun deleteByConversationId(conversationId: UUID): Long =
            delete("conversationId", conversationId)
    }
}
