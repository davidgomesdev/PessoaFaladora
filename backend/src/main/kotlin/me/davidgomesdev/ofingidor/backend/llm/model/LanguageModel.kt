package me.davidgomesdev.ofingidor.backend.llm.model

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel

interface LanguageModel {
    fun chatModel(): ChatModel

    fun streamingChatModel(): StreamingChatModel
}
