package me.davidgomesdev.ofingidor.ui.service

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePut
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.davidgomesdev.ofingidor.shared.dto.ChatEvent
import me.davidgomesdev.ofingidor.shared.dto.DebateEvent
import me.davidgomesdev.ofingidor.shared.dto.Persona
import me.davidgomesdev.ofingidor.shared.dto.json
import me.davidgomesdev.ofingidor.ui.model.DebatePair

const val DEFAULT_HOST = "127.0.0.1"

@Suppress("HttpUrlsUsage")
val apiUrl = "http://${getHost()}:8080"


class ThinkAPI {
    private val client = HttpClient {
        install(HttpTimeout) {
            socketTimeoutMillis = 60_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Napier.v("HTTP Client", null, message)
                }
            }
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
            })
        }
    }.also { Napier.base(DebugAntilog()) }

    private var sessionToken: String? = null
    private var traceparent: String? = null

    internal data class ConversationState(
        val sessionToken: String?,
        val traceparent: String?,
    )

    fun resetConversation() {
        restoreConversation(sessionToken = null, traceparent = null)
    }

    internal fun restoreConversation(
        sessionToken: String?,
        traceparent: String?,
    ) {
        this.sessionToken = sessionToken
        this.traceparent = traceparent
    }

    internal fun conversationState(): ConversationState = ConversationState(
        sessionToken = sessionToken,
        traceparent = traceparent,
    )

    private fun updateConversation(
        sessionToken: String?,
        traceparent: String?,
    ) {
        restoreConversation(
            sessionToken = sessionToken ?: this.sessionToken,
            traceparent = traceparent ?: this.traceparent,
        )
    }

    fun sendThinkRequest(
        query: String,
        persona: Persona
    ): Flow<Result<ChatEvent>> = channelFlow {
        try {
            client.preparePut("$apiUrl${ApiConstants.ENDPOINT_CONVERSATION}") {
                accept(ContentType.Any)
                contentType(ContentType.Application.Json)
                setBody(ThinkPayload(query, persona.codeName))

                sessionToken?.let { header(HttpHeaders.Authorization, "${HttpHeaderConstants.AUTH_SCHEME_BEARER}$it") }
                traceparent?.let { header(HttpHeaderConstants.HEADER_TRACEPARENT_ALT, it) }
            }.execute { httpResponse ->
                updateConversation(
                    sessionToken = httpResponse.headers[HttpHeaderConstants.HEADER_SESSION_TOKEN],
                    traceparent = httpResponse.headers[HttpHeaderConstants.HEADER_TRACEPARENT],
                )

                val channel: ByteReadChannel = httpResponse.body()

                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break

                    if (line.isBlank()) continue

                    Napier.d("ChatEvent received: $line")

                    val event = json.decodeFromString<ChatEvent>(line)

                    send(Result.success(event))
                }
            }
        } catch (e: Throwable) {
            if (!isNetworkException(e)) throw e
            Napier.e("Request failed", e)
            send(Result.failure(e))
        }
    }

    fun sendDebateRequest(
        query: String,
        pair: DebatePair,
    ): Flow<Result<DebateEvent>> = channelFlow {
        try {
            client.preparePut("$apiUrl${ApiConstants.ENDPOINT_DEBATE}") {
                accept(ContentType.Any)
                contentType(ContentType.Application.Json)
                setBody(DebatePayload(query, pair.left.codeName, pair.right.codeName))

                sessionToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                traceparent?.let { header(HttpHeaderConstants.HEADER_TRACEPARENT_ALT, it) }
            }.execute { httpResponse ->
                updateConversation(
                    sessionToken = httpResponse.headers[HttpHeaderConstants.HEADER_SESSION_TOKEN],
                    traceparent = httpResponse.headers[HttpHeaderConstants.HEADER_TRACEPARENT],
                )

                val channel: ByteReadChannel = httpResponse.body()
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break
                    if (line.isBlank()) continue
                    send(Result.success(json.decodeFromString<DebateEvent>(line)))
                }
            }
        } catch (e: Throwable) {
            if (!isNetworkException(e)) throw e
            send(Result.failure(e))
        }
    }
}

@Serializable
data class ThinkPayload(val input: String, val persona: String)

@Serializable
data class DebatePayload(val input: String, val personaA: String, val personaB: String)
