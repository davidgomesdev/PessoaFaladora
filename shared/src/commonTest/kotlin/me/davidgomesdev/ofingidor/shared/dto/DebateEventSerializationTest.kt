package me.davidgomesdev.ofingidor.shared.dto

import kotlin.test.Test
import kotlin.test.assertEquals

class DebateEventSerializationTest {
    @Test
    fun `TurnStart serializes with type discriminator`() {
        val result = json.encodeToString<DebateEvent>(
            DebateEvent.TurnStart(turnIndex = 2, speaker = "alberto_caeiro")
        )

        assertEquals(
            """{"type":"turn_start","turnIndex":2,"speaker":"alberto_caeiro"}""",
            result,
        )
    }

    @Test
    fun `Done serializes with type discriminator`() {
        val result = json.encodeToString<DebateEvent>(DebateEvent.Done)
        assertEquals("""{"type":"done"}""", result)
    }
}
