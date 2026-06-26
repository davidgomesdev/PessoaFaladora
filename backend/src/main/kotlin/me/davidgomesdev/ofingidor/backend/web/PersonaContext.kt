package me.davidgomesdev.ofingidor.backend.web

import jakarta.enterprise.context.RequestScoped
import me.davidgomesdev.ofingidor.shared.dto.Persona

@RequestScoped
class PersonaContext {
    var persona: Persona? = null
}