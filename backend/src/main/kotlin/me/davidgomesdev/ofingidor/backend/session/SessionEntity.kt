package me.davidgomesdev.ofingidor.backend.session

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "sessions")
class SessionEntity : PanacheEntityBase {

    @Id
    @Column(name = "conversation_id", nullable = false)
    lateinit var conversationId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false)
    lateinit var conversationType: ConversationType

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "persona_id", nullable = false)
    lateinit var persona: PersonaEntity

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "opponent_persona_id")
    var opponentPersona: PersonaEntity? = null

    companion object : PanacheCompanionBase<SessionEntity, UUID>
}
