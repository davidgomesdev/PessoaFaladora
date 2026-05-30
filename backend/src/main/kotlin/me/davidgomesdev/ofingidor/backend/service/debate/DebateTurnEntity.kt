package me.davidgomesdev.ofingidor.backend.service.debate

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "debate_turns")
class DebateTurnEntity : PanacheEntityBase {

    @Id
    lateinit var id: UUID

    @Column(name = "conversation_id", nullable = false)
    lateinit var conversationId: UUID

    @Column(name = "turn_index", nullable = false)
    var turnIndex: Int = 0

    @Column(name = "entry_type", nullable = false)
    lateinit var entryType: String

    @Column(name = "speaker_persona_id", nullable = true)
    var speakerPersonaId: String? = null

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    lateinit var text: String

    @Column(name = "sources_json", columnDefinition = "TEXT")
    var sourcesJson: String? = null

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime

    companion object : PanacheCompanionBase<DebateTurnEntity, UUID>
}
