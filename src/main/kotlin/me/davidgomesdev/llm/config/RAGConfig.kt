package me.davidgomesdev.llm.config

import io.smallrye.config.ConfigMapping

@ConfigMapping(prefix = "rag")
interface RAGConfig {
    fun maxResults(): Int
    fun minScore(): Double
}
