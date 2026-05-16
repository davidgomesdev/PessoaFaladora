package me.davidgomesdev.ofingidor.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ChatEvent {

    @Serializable
    @SerialName("start")
    data class Start(val traceId: String) : ChatEvent()

    @Serializable
    @SerialName("token")
    data class Token(val value: String) : ChatEvent()

    @Serializable
    @SerialName("sources")
    data class Sources(val items: List<Source> = listOf()) : ChatEvent() {

        @Serializable
        data class Source(
            val id: Long,
            val title: String,
            val author: String,
            val category: String,
            val score: Int,
        )
    }

    @Serializable
    @SerialName("done")
    data class Done(
        val tokensUsed: Int,
        val timeTaken: String,
    ) : ChatEvent()
}
