package me.davidgomesdev.ofingidor.backend.session

import io.smallrye.config.ConfigMapping
import java.time.Duration

@ConfigMapping(prefix = "session")
interface SessionConfig {
    fun jwt(): JwtConfig

    fun memory(): MemoryConfig

    interface JwtConfig {
        fun secret(): String

        fun ttl(): Duration
    }

    interface MemoryConfig {
        fun maxMessages(): Int
    }
}
