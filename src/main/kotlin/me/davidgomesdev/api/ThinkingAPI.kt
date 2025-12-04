package me.davidgomesdev.api

import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import kotlinx.serialization.json.Json
import me.davidgomesdev.source.PessoaCategory
import org.jboss.logging.Logger
import java.io.File
import java.nio.file.Files

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

    @POST
    fun convert() {
        log.info("Converting all texts to markdown")

        val texts = Files.readString(kotlin.io.path.Path("assets/all_texts.json"))

        val categoriesDecoded = Json.decodeFromString<Array<PessoaCategory>>(texts)

        val oneCategory = categoriesDecoded[0]
        val oneCategoryMd = writeMd(oneCategory)

        File("assets/one_category.md").writeText(oneCategoryMd)

        Files.writeString(
            kotlin.io.path.Path("assets/all_texts.md"),
            categoriesDecoded.joinToString(separator = "\n") { writeMd(it) })

        log.info("Finished conversion")
    }

    private fun writeMd(category: PessoaCategory, level: Int = 1): String {
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
