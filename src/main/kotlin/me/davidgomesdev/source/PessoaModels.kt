package me.davidgomesdev.source

import kotlinx.serialization.Serializable

@Serializable
data class PessoaCategory(
    val id: Int,
    val title: String,
    val parentCategory: PessoaCategory? = null,
    val subcategories: List<PessoaCategory>,
    val texts: List<PessoaText>?,
)

@Serializable
data class PessoaText(
    val id: Int,
    val title: String,
    val content: String,
    val author: String,
)
