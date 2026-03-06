package me.davidgomesdev.web

import io.opentelemetry.api.trace.Span
import io.smallrye.common.annotation.Blocking
import io.smallrye.mutiny.Multi
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import me.davidgomesdev.service.ChatService
import org.jboss.logging.Logger
import org.jboss.resteasy.reactive.RestMulti

@Path("/pensa")
class ThinkingAPI(val chatService: ChatService) {

    val log: Logger = Logger.getLogger(this::class.java)

    @PUT
    @Blocking
    @Produces(MediaType.TEXT_PLAIN)
    fun queryModel(body: QueryPayload): Multi<String> {
        val traceId = Span.current().spanContext.traceId

        log.info("Querying model with trace ID: $traceId")

        return RestMulti
            .fromMultiData(chatService.query(body.input))
            .header("X-Trace-Id", Span.current().spanContext.traceId).build()
    }
}

data class QueryPayload(val input: String)
