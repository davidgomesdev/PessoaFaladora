package me.davidgomesdev.ofingidor.backend.model

import me.davidgomesdev.ofingidor.backend.dto.PessoaCategoryDto
import me.davidgomesdev.ofingidor.backend.dto.PessoaTextDto

data class PessoaCategory(
    val rootCategoryId: Int?,
    val id: Int,
    val title: String,
    val subcategories: List<PessoaCategory>,
    val texts: List<PessoaText>,
) {
    companion object {
        fun fromRootCategory(dto: PessoaCategoryDto) =
            PessoaCategory(
                null,
                dto.id,
                dto.title,
                dto.subcategories.map {
                    from(dto.id, it)
                },
                (dto.texts ?: listOf()).map { PessoaText.from(dto.id, it) },
            )

        fun from(
            rootCategoryId: Int,
            category: PessoaCategory,
        ): PessoaCategory =
            PessoaCategory(
                rootCategoryId,
                category.id,
                category.title,
                category.subcategories.map {
                    from(rootCategoryId, it)
                },
                category.texts,
            )

        fun from(
            rootCategoryId: Int,
            dto: PessoaCategoryDto,
        ): PessoaCategory =
            PessoaCategory(
                rootCategoryId,
                dto.id,
                dto.title,
                dto.subcategories.map {
                    from(rootCategoryId, it)
                },
                (dto.texts ?: listOf()).map { PessoaText.from(dto.id, it) },
            )
    }
}

data class PessoaText(
    val rootCategoryId: Int,
    val id: Int,
    val title: String,
    val content: String,
    val author: String,
) {
    companion object {
        fun from(
            rootCategoryId: Int,
            dto: PessoaTextDto,
        ) = PessoaText(rootCategoryId, dto.id, dto.title, dto.content, dto.author)
    }
}
