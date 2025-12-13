package me.davidgomesdev.api

import io.smallrye.common.annotation.Blocking
import io.smallrye.mutiny.Multi
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import org.jboss.logging.Logger

@Path("/pensa")
class ThinkingAPI(val chatService: ChatService) {

    val log: Logger = Logger.getLogger(this::class.java)

    @PUT
    @Blocking
    fun queryModel(body: QueryPayload): Multi<String> {
        log.info("Querying model")

        val response = chatService.query(body.input)

        return response
    }
}

data class QueryPayload(val input: String)

data class QueryResponse(val response: String, val sources: List<String>)
