package me.davidgomesdev.ofingidor.backend.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(ThinkingAPIMemoryTestProfile::class)
class ThinkingAPIMemoryTest {

    @Test
    fun `first request with no Authorization returns X-Session-Token header`() {
        val response = given()
            .contentType("application/json")
            .body("""{"input": "Quem és tu?", "persona": "alberto_caeiro"}""")
            .`when`()
            .put("/pensa/conversation")
            .then()
            .statusCode(200)
            .extract()
            .response()

        assertNotNull(response.header("X-Session-Token"), "X-Session-Token header must be present on first request")
    }

    @Test
    fun `subsequent request with valid token does not return X-Session-Token`() {
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

        val secondResponse = given()
            .contentType("application/json")
            .header("Authorization", "Bearer $token")
            .body("""{"input": "Fala mais sobre ti.", "persona": "alberto_caeiro"}""")
            .`when`()
            .put("/pensa/conversation")
            .then()
            .statusCode(200)
            .extract()
            .response()

        assertNull(
            secondResponse.header("X-Session-Token"),
            "X-Session-Token must not be returned on subsequent requests"
        )
    }

    @Test
    fun `mismatched persona returns 409`() {
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
            .body("""{"input": "Fala mais.", "persona": "ricardo_reis"}""")
            .`when`()
            .put("/pensa/conversation")
            .then()
            .statusCode(409)
    }

    @Test
    fun `debate token reused on single chat returns 409`() {
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
            .body("""{"input": "Quem és tu?", "persona": "fernando_pessoa"}""")
            .`when`()
            .put("/pensa/conversation")
            .then()
            .statusCode(409)
    }

    @Test
    fun `invalid JWT returns 401`() {
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer not.a.real.token")
            .body("""{"input": "Quem és tu?", "persona": "alberto_caeiro"}""")
            .`when`()
            .put("/pensa/conversation")
            .then()
            .statusCode(401)
    }
}
