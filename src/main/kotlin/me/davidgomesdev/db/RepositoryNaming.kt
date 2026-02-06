package me.davidgomesdev.db

import jakarta.enterprise.context.ApplicationScoped
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

@ApplicationScoped
class RepositoryNaming : PhysicalNamingStrategy {

    override fun toPhysicalTableName(name: Identifier?, context: JdbcEnvironment): Identifier {
        val table = if (System.getenv("PREVIEW_ONLY") == "true") "embeddings_preview" else "embeddings"
        return Identifier.toIdentifier(table)
    }

    override fun toPhysicalCatalogName(name: Identifier?, context: JdbcEnvironment): Identifier? {
        return name
    }

    override fun toPhysicalSchemaName(name: Identifier?, context: JdbcEnvironment): Identifier? {
        return name
    }

    override fun toPhysicalSequenceName(name: Identifier?, context: JdbcEnvironment): Identifier? {
        return name
    }

    override fun toPhysicalColumnName(name: Identifier?, context: JdbcEnvironment): Identifier? {
        return name
    }
}
