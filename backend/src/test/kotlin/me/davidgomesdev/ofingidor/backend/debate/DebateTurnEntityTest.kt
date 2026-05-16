package me.davidgomesdev.ofingidor.backend.debate

import jakarta.persistence.Column
import jakarta.persistence.Lob
import me.davidgomesdev.ofingidor.backend.service.debate.DebateTurnEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DebateTurnEntityTest {

    @Test
    fun `text columns use postgres text mapping without lob`() {
        val textField = DebateTurnEntity::class.java.getDeclaredField("text")
        val sourcesField = DebateTurnEntity::class.java.getDeclaredField("sourcesJson")

        assertEquals("TEXT", textField.getAnnotation(Column::class.java).columnDefinition)
        assertNull(textField.getAnnotation(Lob::class.java))

        assertEquals("TEXT", sourcesField.getAnnotation(Column::class.java).columnDefinition)
        assertNull(sourcesField.getAnnotation(Lob::class.java))
    }
}
