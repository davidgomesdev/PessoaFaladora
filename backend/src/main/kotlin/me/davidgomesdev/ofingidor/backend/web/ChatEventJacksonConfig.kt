package me.davidgomesdev.ofingidor.backend.web

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton
import me.davidgomesdev.ofingidor.shared.dto.ChatEvent
import me.davidgomesdev.ofingidor.shared.dto.DebateEvent

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatEvent.Start::class, name = "start"),
    JsonSubTypes.Type(value = ChatEvent.Token::class, name = "token"),
    JsonSubTypes.Type(value = ChatEvent.Sources::class, name = "sources"),
    JsonSubTypes.Type(value = ChatEvent.Done::class, name = "done"),
)
private interface ChatEventMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DebateEvent.Start::class, name = "start"),
    JsonSubTypes.Type(value = DebateEvent.TurnStart::class, name = "turn_start"),
    JsonSubTypes.Type(value = DebateEvent.Token::class, name = "token"),
    JsonSubTypes.Type(value = DebateEvent.Sources::class, name = "sources"),
    JsonSubTypes.Type(value = DebateEvent.TurnDone::class, name = "turn_done"),
    JsonSubTypes.Type(value = DebateEvent.Done::class, name = "done"),
)
private interface DebateEventMixin

@Singleton
class ChatEventJacksonConfig : ObjectMapperCustomizer {
    override fun customize(objectMapper: ObjectMapper) {
        objectMapper.addMixIn(ChatEvent::class.java, ChatEventMixin::class.java)
        objectMapper.addMixIn(DebateEvent::class.java, DebateEventMixin::class.java)
    }
}
