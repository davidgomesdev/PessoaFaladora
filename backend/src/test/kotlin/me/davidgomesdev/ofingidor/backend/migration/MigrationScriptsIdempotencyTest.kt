package me.davidgomesdev.ofingidor.backend.migration

import org.h2.tools.RunScript
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

class MigrationScriptsIdempotencyTest {

    @Test
    fun `migration scripts can be applied more than once`() {
        DriverManager.getConnection(
            "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        ).use { connection ->
            repeat(2) {
                migrationResources()
                    .also { assertFalse(it.isEmpty()) }
                    .forEach { migration ->
                        runMigration(connection, migration)
                    }
            }
        }
    }

    private fun runMigration(connection: java.sql.Connection, migration: String) {
        val inputStream = requireNotNull(javaClass.classLoader.getResourceAsStream(migration)) {
            "Missing migration resource: $migration"
        }

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            RunScript.execute(connection, reader)
        }
    }

    private companion object {
        fun migrationResources(): List<String> {
            val migrationDirectory = listOf(
                Path.of("backend", "src", "main", "resources", "db", "migration"),
                Path.of("src", "main", "resources", "db", "migration"),
            ).firstOrNull(Files::exists)
                ?: error("Could not locate db/migration directory")

            return Files.list(migrationDirectory).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .map { path -> "db/migration/${path.fileName}" }
                    .sorted()
                    .toList()
            }
        }
    }
}
