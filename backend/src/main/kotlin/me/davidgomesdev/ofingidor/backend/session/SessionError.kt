package me.davidgomesdev.ofingidor.backend.session

enum class SessionError(val httpStatus: Int) {
    INVALID_TOKEN(401),
    SESSION_NOT_FOUND(404),
    PERSONA_MISMATCH(409),
    PERSONA_PAIR_MISMATCH(409),
    SESSION_MODE_MISMATCH(409),
}
