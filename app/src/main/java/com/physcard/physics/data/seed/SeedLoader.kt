package com.physcard.physics.data.seed

import android.content.Context
import com.physcard.physics.domain.model.Ability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

class SeedException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ValidationIssue(val path: String, val message: String)

class SeedValidationException(val issues: List<ValidationIssue>) :
    Exception(issues.take(6).joinToString("；") { "${it.path}：${it.message}" })

class SeedLoader(
    private val context: Context,
    private val json: Json,
) {
    fun loadBuiltIn(): SeedBundle {
        val modules = readAsset("modules.json") { text ->
            json.decodeFromString<List<ModuleSeedDto>>(text)
        }
        val formulas = readAsset("formulas.json") { text ->
            json.decodeFromString<List<FormulaSeedDto>>(text)
        }
        val questions = readAsset("questions.json") { text ->
            json.decodeFromString<List<QuestionSeedDto>>(text)
        }
        val bundle = SeedBundle(modules, formulas, questions)
        validate(bundle, requireAllAbilities = true)
        return bundle
    }

    fun parseExport(text: String): ExportBundleDto {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw SeedException("文件不是有效的 JSON 对象。", e)
        }
        val schema = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw SeedException("缺少 schemaVersion 字段，无法确认文件格式。")
        if (schema != 1) {
            throw SeedException("不支持的题库版本：$schema。当前应用支持版本 1。")
        }
        if ("formulas" !in root || root["formulas"]?.jsonArray == null) {
            throw SeedException("文件中缺少 formulas 数组。")
        }
        if ("questions" !in root || root["questions"]?.jsonArray == null) {
            throw SeedException("文件中缺少 questions 数组。")
        }
        return try {
            json.decodeFromString<ExportBundleDto>(text)
        } catch (e: Exception) {
            throw SeedException("题库字段格式不完整：${e.message ?: "无法解析"}", e)
        }
    }

    fun validate(bundle: SeedBundle, requireAllAbilities: Boolean) {
        val issues = mutableListOf<ValidationIssue>()
        val moduleIds = bundle.modules.map { it.id }.toSet()
        if (moduleIds.isEmpty()) {
            issues += ValidationIssue("modules", "至少需要一个物理模块")
        }
        bundle.modules.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            issues += ValidationIssue("modules/$it", "模块 id 重复")
        }

        val formulasById = mutableMapOf<String, FormulaSeedDto>()
        val formulaKeys = mutableSetOf<String>()
        bundle.formulas.forEachIndexed { index, formula ->
            val path = "formulas[$index]"
            if (formula.id.isBlank()) issues += ValidationIssue(path, "id 不能为空")
            if (formula.title.isBlank()) issues += ValidationIssue(path, "标题不能为空")
            if (formula.latex.isBlank()) issues += ValidationIssue(path, "公式不能为空")
            if (formula.conditions.isBlank()) issues += ValidationIssue(path, "适用条件不能为空")
            if (formula.module !in moduleIds) {
                issues += ValidationIssue(path, "未知模块 ${formula.module}")
            }
            if (formula.variables.isEmpty()) {
                issues += ValidationIssue(path, "至少需要一个物理量")
            }
            formula.variables.forEachIndexed { variableIndex, variable ->
                if (variable.symbol.isBlank() || variable.name.isBlank() || variable.unit.isBlank()) {
                    issues += ValidationIssue("$path.variables[$variableIndex]", "符号、名称和单位都必须填写")
                }
            }
            val key = "${formula.title.trim()}::${formula.latex.trim()}"
            if (!formulaKeys.add(key)) {
                issues += ValidationIssue(path, "标题与公式完全相同的重复条目")
            }
            if (formula.id in formulasById) {
                issues += ValidationIssue(path, "公式 id 重复：${formula.id}")
            }
            formulasById[formula.id] = formula
        }

        bundle.formulas.forEachIndexed { index, formula ->
            formula.relatedIds.forEach { related ->
                if (related != formula.id && related !in formulasById) {
                    issues += ValidationIssue(
                        "formulas[$index].relatedIds",
                        "关联公式不存在：$related",
                    )
                }
            }
        }

        val questionKeys = mutableSetOf<String>()
        val abilitiesByFormula = mutableMapOf<String, MutableSet<Ability>>()
        bundle.questions.forEachIndexed { index, question ->
            val path = "questions[$index]"
            if (question.id.isBlank()) issues += ValidationIssue(path, "id 不能为空")
            if (question.id in questionKeys) {
                issues += ValidationIssue(path, "题目 id 重复：${question.id}")
            }
            questionKeys += question.id
            if (question.formulaId !in formulasById) {
                issues += ValidationIssue(path, "引用了不存在的公式 ${question.formulaId}")
            }
            if (question.prompt.isBlank()) issues += ValidationIssue(path, "题干不能为空")
            if (question.explanation.isBlank()) issues += ValidationIssue(path, "解析不能为空")
            val ability = try {
                Ability.fromWire(question.ability)
            } catch (e: IllegalArgumentException) {
                issues += ValidationIssue(path, "未知能力 ${question.ability}")
                null
            }
            if (question.options.size != 4) {
                issues += ValidationIssue(path, "必须有 4 个选项，当前为 ${question.options.size}")
            }
            if (question.correctIndex !in question.options.indices) {
                issues += ValidationIssue(path, "correctIndex 越界")
            }
            val optionValues = question.options.map { it.latex ?: it.text ?: "" }
            if (optionValues.any { it.isBlank() }) {
                issues += ValidationIssue(path, "选项内容不能为空")
            }
            if (optionValues.toSet().size != optionValues.size) {
                issues += ValidationIssue(path, "选项内容重复")
            }
            if (question.options.count { !it.latex.isNullOrBlank() } !in listOf(0, 4)) {
                issues += ValidationIssue(path, "同一道题的选项应全部为公式或全部为文字")
            }
            if (ability != null && question.formulaId in formulasById) {
                abilitiesByFormula.getOrPut(question.formulaId) { mutableSetOf() }.add(ability)
            }
        }

        if (requireAllAbilities) {
            bundle.formulas.forEach { formula ->
                val present = abilitiesByFormula[formula.id].orEmpty()
                Ability.entries.filterNot { it in present }.forEach { missing ->
                    issues += ValidationIssue(
                        "formulas/${formula.id}",
                        "缺少「${missing.title}」题目",
                    )
                }
            }
        } else {
            bundle.formulas.forEach { formula ->
                if (abilitiesByFormula[formula.id].isNullOrEmpty()) {
                    issues += ValidationIssue("formulas/${formula.id}", "该公式没有任何题目")
                }
            }
        }

        if (issues.isNotEmpty()) {
            throw SeedValidationException(issues)
        }
    }

    private fun <T> readAsset(name: String, decode: (String) -> T): T {
        val text = try {
            context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw SeedException("无法读取内置题库 $name。", e)
        }
        return try {
            decode(text)
        } catch (e: Exception) {
            throw SeedException("内置题库 $name 格式损坏：${e.message ?: "解析失败"}", e)
        }
    }
}
