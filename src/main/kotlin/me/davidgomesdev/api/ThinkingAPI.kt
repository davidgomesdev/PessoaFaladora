package me.davidgomesdev.api

import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import org.jboss.logging.Logger

@Path("/pensa")
class ThinkingAPI(val service: Service) {

    val log: Logger = Logger.getLogger(this::class.java)

    @PUT
    fun queryModel(body: QueryPayload): QueryResponse {
        log.info("Querying model")

        val response = service.query(body.input)

        log.info("Finished querying")

        return QueryResponse(response)
    }
}

data class QueryPayload(val input: String)

data class QueryResponse(val response: String)
