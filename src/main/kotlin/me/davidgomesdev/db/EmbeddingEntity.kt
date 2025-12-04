package me.davidgomesdev.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

/// Skipped most fields, just need this one
@Entity
@Table(name = "alberto_caeiro_embeddings")
class EmbeddingEntity(
    @Id
    @Column(name = "embedding_id")
    val embeddingId: UUID,
    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    val metadata: String,
)
