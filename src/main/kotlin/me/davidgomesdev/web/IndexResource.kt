package me.davidgomesdev.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty

@Path("/")
class IndexResource(
    @param:Location("index.html") val index: Template,
    @param:ConfigProperty(name = "pessoa.url") val pessoaUrl: String
) {

    @GET
    @Produces(MediaType.TEXT_HTML)
    fun index(): TemplateInstance {
        return index.data("pessoaUrl", pessoaUrl)
    }
}


