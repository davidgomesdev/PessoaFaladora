package me.davidgomesdev.ofingidor.ui.model

import me.davidgomesdev.ofingidor.shared.dto.ChatEvent

data class Source(
    val id: Long,
    val title: String,
    val author: String,
    val category: String,
    val score: Int,
) {
    companion object {
        fun from(event: ChatEvent.Sources.Source) =
            Source(
                event.id,
                event.title,
                event.author,
                event.category,
                event.score,
            )
    }
}
