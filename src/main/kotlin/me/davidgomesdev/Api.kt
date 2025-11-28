package me.davidgomesdev

import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path


@Path("/pensa")
class Api(val service: Service) {
    @PUT
    fun queryModel(body: QueryPayload): QueryResponse {
        val response = service.query(body.input)

        return QueryResponse(response)
    }
}

data class QueryPayload(val input: String)

data class QueryResponse(val response: String)
