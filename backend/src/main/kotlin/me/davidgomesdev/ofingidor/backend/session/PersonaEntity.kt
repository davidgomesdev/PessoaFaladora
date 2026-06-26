package me.davidgomesdev.ofingidor.backend.session

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "personas")
class PersonaEntity : PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int = 0

    @Column(name = "code_name", nullable = false, unique = true)
    lateinit var codeName: String

    companion object : PanacheCompanionBase<PersonaEntity, Int> {
        fun findByCodeName(codeName: String): PersonaEntity? = find("codeName", codeName).firstResult()
    }
}
