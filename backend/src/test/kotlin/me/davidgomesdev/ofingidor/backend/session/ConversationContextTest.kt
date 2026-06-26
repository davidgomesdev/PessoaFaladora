package me.davidgomesdev.ofingidor.backend.session

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(SessionServiceTestProfile::class)
class ConversationContextTest {
    @Inject
    lateinit var context: ConversationContext

    @BeforeEach
    fun reset() {
        context.conversationId = null
    }

    @Test
    fun `conversationId is null by default`() {
        assertNull(context.conversationId)
    }

    @Test
    fun `conversationId can be set`() {
        context.conversationId = "test-id"
        assertEquals("test-id", context.conversationId)
    }
}
