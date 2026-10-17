package com.physcard.physics.data.seed

import kotlinx.serialization.Serializable

@Serializable
data class ModuleSeedDto(
    val id: String,
    val name: String,
    val description: String = "",
    val accent: String = "#2F6F55",
    val orderIndex: Int = 0,
)

@Serializable
data class VariableSeedDto(
    val symbol: String,
    val name: String,
    val unit: String,
)

@Serializable
data class ConfusionSeedDto(
    val wrong: String,
    val why: String,
)

@Serializable
data class FormulaSeedDto(
    val id: String,
    val title: String,
    val module: String,
    val chapter: String,
    val textbook: String = "人教版高中物理（2019 版）",
    val latex: String,
    val conditions: String,
    val variables: List<VariableSeedDto> = emptyList(),
    val confusions: List<ConfusionSeedDto> = emptyList(),
    val relatedIds: List<String> = emptyList(),
    val orderIndex: Int = 0,
)

@Serializable
data class OptionSeedDto(
    val latex: String? = null,
    val text: String? = null,
)

@Serializable
data class QuestionSeedDto(
    val id: String,
    val formulaId: String,
    val ability: String,
    val prompt: String,
    val stimulusLatex: String? = null,
    val options: List<OptionSeedDto>,
    val correctIndex: Int,
    val explanation: String,
)

@Serializable
data class ExportBundleDto(
    val schemaVersion: Int = 1,
    val appVersion: String = "",
    val exportedAt: String = "",
    val formulas: List<FormulaSeedDto> = emptyList(),
    val questions: List<QuestionSeedDto> = emptyList(),
)

data class SeedBundle(
    val modules: List<ModuleSeedDto>,
    val formulas: List<FormulaSeedDto>,
    val questions: List<QuestionSeedDto>,
)
