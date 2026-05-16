package me.davidgomesdev.ofingidor.shared.dto

import kotlinx.serialization.json.Json

val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true }
