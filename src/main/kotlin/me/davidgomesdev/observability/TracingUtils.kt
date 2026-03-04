package me.davidgomesdev.observability

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder

fun attributes(modifier: AttributesBuilder.() -> Unit): Attributes = Attributes.builder().apply(modifier).build()
