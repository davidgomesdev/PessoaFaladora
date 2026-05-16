package me.davidgomesdev.ofingidor.backend.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(ThinkingAPIDebateTestProfile::class)
class ThinkingAPIDebateTest {

    @Test
    fun `first debate request returns session and tracing headers`() {
        val response = given()
            .contentType("application/json")
            .body(
                """
                {"input": "Debatam a poesia.", "personaA": "fernando_pessoa", "personaB": "alberto_caeiro"}
                """.trimIndent()
            )
            .`when`()
            .put("/pensa/debate")
            .then()
            .statusCode(200)
            .extract()
            .response()

        assertNotNull(response.header("X-Session-Token"))
        assertNotNull(response.header("X-Trace-Id"))
        assertTrue(response.header("X-Traceparent").startsWith("00-"))
    }

    @Test
    fun `debate request completes all four turns when assistant callbacks are async`() {
        val body = given()
            .contentType("application/json")
            .body(
                """
                {"input": "Debatam a poesia.", "personaA": "fernando_pessoa", "personaB": "alberto_caeiro"}
                """.trimIndent()
            )
            .`when`()
            .put("/pensa/debate")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString()

        assertEquals(4, "\"type\":\"turn_start\"".toRegex().findAll(body).count())
        assertTrue(body.contains("\"type\":\"done\""))
    }

    @Test
    fun `duplicate personas returns 400`() {
        given()
            .contentType("application/json")
            .body(
                """
                {"input": "Debatam a poesia.", "personaA": "fernando_pessoa", "personaB": "fernando_pessoa"}
                """.trimIndent()
            )
            .`when`()
            .put("/pensa/debate")
            .then()
            .statusCode(400)
    }

    @Test
    fun `mismatched pair on follow up returns 409`() {
        val firstResponse = given()
            .contentType("application/json")
            .body(
                """
                {"input": "Debatam a poesia.", "personaA": "fernando_pessoa", "personaB": "alberto_caeiro"}
                """.trimIndent()
            )
            .`when`()
            .put("/pensa/debate")
            .then()
            .statusCode(200)
            .extract()
            .response()

        val token = firstResponse.header("X-Session-Token")
        assertNotNull(token)

        given()
            .contentType("application/json")
            .header("Authorization", "Bearer $token")
            .body(
                """
                {"input": "Continuem.", "personaA": "fernando_pessoa", "personaB": "ricardo_reis"}
                """.trimIndent()
            )
            .`when`()
            .put("/pensa/debate")
            .then()
            .statusCode(409)
    }

    @Test
    fun `single chat token reused for debate returns 409`() {
        val firstResponse = given()
            .contentType("application/json")
            .body("""{"input": "Quem és tu?", "persona": "alberto_caeiro"}""")
            .`when`()
            .put("/pensa/conversation")
            .then()
            .statusCode(200)
            .extract()
            .response()

        val token = firstResponse.header("X-Session-Token")
        assertNotNull(token)

        given()
            .contentType("application/json")
            .header("Authorization", "Bearer $token")
            .body(
                """
                {"input": "Agora debatam.", "personaA": "fernando_pessoa", "personaB": "alberto_caeiro"}
                """.trimIndent()
            )
            .`when`()
            .put("/pensa/debate")
            .then()
            .statusCode(409)
    }
}
