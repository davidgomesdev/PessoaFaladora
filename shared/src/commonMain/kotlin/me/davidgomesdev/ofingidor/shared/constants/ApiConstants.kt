package me.davidgomesdev.ofingidor.shared.constants

object DebateConstants {
    // Debate turn entry types
    const val DEBATE_ENTRY_TYPE_USER_PROMPT = "user_prompt"
    const val DEBATE_ENTRY_TYPE_PERSONA_TURN = "persona_turn"
}

object HttpConstants {
    // HTTP Headers
    const val HEADER_SESSION_TOKEN = "X-Session-Token"
    const val HEADER_TRACEPARENT = "X-Traceparent"
    const val HEADER_TRACEPARENT_ALT = "traceparent"

    // Content types
    const val CONTENT_TYPE_NDJSON = "application/x-ndjson"
}
