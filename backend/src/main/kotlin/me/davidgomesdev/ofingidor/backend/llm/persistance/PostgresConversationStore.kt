package me.davidgomesdev.ofingidor.backend.llm.persistance

import com.github.f4b6a3.uuid.UuidCreator
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ChatMessageDeserializer
import dev.langchain4j.data.message.ChatMessageSerializer
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class PostgresConversationStore : ChatMemoryStore {
    private val log: Logger = Logger.getLogger(this::class.java)

    @Transactional
    override fun getMessages(memoryId: Any): List<ChatMessage> {
        val conversationId = UUID.fromString(memoryId.toString())
        val messages =
            ChatMemoryEntity
                .findByConversationIdOrdered(conversationId)
                .map { ChatMessageDeserializer.messageFromJson(it.messageJson) }

        log.debug("Loaded ${messages.size} messages for conversationId=$conversationId")

        return messages
    }

    @Transactional
    override fun updateMessages(
        memoryId: Any,
        messages: List<ChatMessage>,
    ) {
        val conversationId = UUID.fromString(memoryId.toString())

        ChatMemoryEntity.deleteByConversationId(conversationId)

        val now = OffsetDateTime.now()

        messages.forEach { message ->
            val entity = ChatMemoryEntity()

            entity.id = UuidCreator.getTimeOrderedEpoch()
            entity.conversationId = conversationId
            entity.messageJson = ChatMessageSerializer.messageToJson(message)
            entity.createdAt = now

            entity.persist()
        }

        log.debug("Stored ${messages.size} messages for conversationId=$conversationId")
    }

    @Transactional
    override fun deleteMessages(memoryId: Any) {
        val conversationId = UUID.fromString(memoryId.toString())
        val deleted = ChatMemoryEntity.deleteByConversationId(conversationId)

        log.info("Deleted $deleted chat memory rows for conversationId=$conversationId")
    }
}
