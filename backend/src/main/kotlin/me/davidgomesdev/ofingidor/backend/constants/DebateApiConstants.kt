package me.davidgomesdev.ofingidor.backend.constants

object DebateApiConstants {
    // API Paths
    const val DEBATE_ENDPOINT = "/debate"
    const val CONVERSATION_ENDPOINT = "/conversation"

    // Span names
    const val SPAN_NAME_QUERY_MODEL = "API QueryModel"
    const val SPAN_NAME_QUERY_DEBATE = "API QueryDebate"
    const val SPAN_NAME_DEBATE_TURN = "Debate turn"

    // Content type
    const val CONTENT_TYPE_NDJSON = "application/x-ndjson"
}
