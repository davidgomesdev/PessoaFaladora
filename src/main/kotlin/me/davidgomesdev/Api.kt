package me.davidgomesdev

import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import kotlinx.serialization.json.Json
import java.nio.file.Files


@Path("/pensa")
class Api(val service: Service) {
    @PUT
    fun queryModel(body: QueryPayload): QueryResponse {

        val response = service.query(body.input)

        return QueryResponse(response)
    }

    @POST
    fun convert() {
        println("Deserializing")

        val texts = Files.readString(kotlin.io.path.Path("assets/all_texts.json"))

        val textsDecoded = Json.decodeFromString<Array<PessoaCategory>>(texts)

        Files.writeString(
            kotlin.io.path.Path("all.md"),
            textsDecoded.joinToString(separator = "\n") { writeMd(it, 1) })
    }

    private fun writeMd(category: PessoaCategory, level: Int): String {
        val sub = category.subcategories.map { writeMd(it, level + 1) }
        val currentHeader = "#".repeat(level)
        val categoryHeader = "$currentHeader ${category.title}\n\n"
        val texts: List<String> = category.texts?.map {
            "$currentHeader# ${it.title}\n\n${it.content}\n"
        } ?: listOf()

        return categoryHeader + texts.joinToString(separator = "\n") +
                sub.joinToString(
                    separator = "\n"
                )
    }
}

data class QueryPayload(val input: String)

data class QueryResponse(val response: String)
