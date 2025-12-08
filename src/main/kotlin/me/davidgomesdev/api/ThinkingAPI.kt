package me.davidgomesdev.api

import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import org.jboss.logging.Logger

@Path("/pensa")
class ThinkingAPI(val chatService: ChatService) {

    val log: Logger = Logger.getLogger(this::class.java)

    @PUT
    fun queryModel(body: QueryPayload): QueryResponse {
        log.info("Querying model")

        val response = chatService.query(body.input)

        log.info("Finished querying")

        return response
    }
}

data class QueryPayload(val input: String)

data class QueryResponse(val response: String, val sources: List<String>)
