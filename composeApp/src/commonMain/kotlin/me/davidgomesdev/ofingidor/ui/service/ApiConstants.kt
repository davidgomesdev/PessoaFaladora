package me.davidgomesdev.ofingidor.ui.service

object ApiConstants {
    // API Endpoints
    const val ENDPOINT_CONVERSATION = "/pensa/conversation"
    const val ENDPOINT_DEBATE = "/pensa/debate"
}

object HttpHeaderConstants {
    // HTTP Headers
    const val HEADER_SESSION_TOKEN = "X-Session-Token"
    const val HEADER_TRACEPARENT = "X-Traceparent"
    const val HEADER_TRACEPARENT_ALT = "traceparent"

    // HTTP Authorization
    const val AUTH_SCHEME_BEARER = "Bearer "
}
