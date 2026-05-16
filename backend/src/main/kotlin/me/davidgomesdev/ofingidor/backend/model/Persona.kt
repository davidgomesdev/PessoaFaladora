package me.davidgomesdev.ofingidor.backend.model

import me.davidgomesdev.ofingidor.shared.dto.Persona

fun Persona.getSystemPromptFileName() =
    if (this == Persona.O_FINGIDOR || this == Persona.FERNANDO_PESSOA)
        "system_message.txt"
    else
        "system_message_$codeName.txt"
