package me.davidgomesdev.db

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class EmbeddingRepository: PanacheRepository<EmbeddingEntity> {
}
