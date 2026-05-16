package me.davidgomesdev.ofingidor.backend.llm

import jakarta.enterprise.context.RequestScoped
import me.davidgomesdev.ofingidor.shared.dto.Persona

@RequestScoped
class PersonaContext {
    var persona: Persona? = null
}

