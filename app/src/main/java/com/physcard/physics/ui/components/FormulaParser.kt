package com.physcard.physics.ui.components

import kotlin.math.max

sealed interface FormulaNode {
    data class Text(
        val text: String,
        val italic: Boolean = true,
        val sup: String? = null,
        val sub: String? = null,
    ) : FormulaNode

    data class Fraction(
        val numerator: List<FormulaNode>,
        val denominator: List<FormulaNode>,
    ) : FormulaNode

    data class Radical(val content: List<FormulaNode>) : FormulaNode
    data object Blank : FormulaNode
    data class Boxed(val content: List<FormulaNode>) : FormulaNode
    data class Overline(val content: List<FormulaNode>) : FormulaNode
    data class Vector(val content: List<FormulaNode>) : FormulaNode
    data class Scripted(
        val base: FormulaNode,
        val sup: List<FormulaNode>? = null,
        val sub: List<FormulaNode>? = null,
    ) : FormulaNode

    data class Space(val widthEm: Float = 0.18f) : FormulaNode
}

class FormulaParseException(message: String, val position: Int) : Exception("$message @$position")

/**
 * A deliberately small TeX subset renderer. Every formula shipped by the app is written in this
 * subset, so the UI never depends on a WebView, network fonts or bitmap formula screenshots.
 */
class FormulaParser(private val source: String) {
    private var position = 0

    fun parse(): List<FormulaNode> {
        val nodes = parseSequence(until = null)
        skipWhitespace()
        if (position != source.length) {
            throw FormulaParseException("公式末尾有多余字符", position)
        }
        return trimSpaces(nodes)
    }

    private fun parseSequence(until: Char?): MutableList<FormulaNode> {
        val nodes = mutableListOf<FormulaNode>()
        while (position < source.length) {
            val char = source[position]
            if (until != null && char == until) break
            when {
                char.isWhitespace() -> {
                    skipWhitespace()
                    if (nodes.isNotEmpty() && nodes.last() !is FormulaNode.Space) {
                        nodes += FormulaNode.Space()
                    }
                }
                char == '\\' -> {
                    val node = parseCommand()
                    if (node != null) nodes += node
                }
                char == '{' -> {
                    // Braces outside a script are grouping only; render their contents inline.
                    nodes += parseGroup()
                }
                char == '_' || char == '^' -> {
                    attachScript(nodes, isSup = char == '^')
                }
                else -> {
                    val run = readTextRun()
                    appendTextRun(nodes, run)
                }
            }
        }
        if (until != null && (position >= source.length || source[position] != until)) {
            throw FormulaParseException("缺少闭合符号 $until", position)
        }
        return nodes
    }

    private fun parseCommand(): FormulaNode? {
        val commandStart = position
        position++ // backslash
        if (position >= source.length) {
            throw FormulaParseException("反斜杠后缺少命令", commandStart)
        }
        val single = source[position]
        if (!single.isLetter()) {
            position++
            return when (single) {
                ',', ':' -> FormulaNode.Space(0.12f)
                ';' -> FormulaNode.Space(0.28f)
                '!' -> null
                ' ' -> FormulaNode.Space(0.3f)
                '%' -> FormulaNode.Text("%", italic = false)
                '{', '}' -> FormulaNode.Text(single.toString(), italic = false)
                else -> throw FormulaParseException("不支持的命令 \\$single", commandStart)
            }
        }
        val nameStart = position
        while (position < source.length && source[position].isLetter()) position++
        val name = source.substring(nameStart, position)
        return when (name) {
            "frac" -> {
                val numerator = parseGroup()
                skipWhitespace()
                val denominator = parseGroup()
                FormulaNode.Fraction(numerator, denominator)
            }
            "sqrt" -> FormulaNode.Radical(parseGroup())
            "boxed" -> {
                val content = parseGroup()
                if (content.size == 1 && content.first() is FormulaNode.Text &&
                    (content.first() as FormulaNode.Text).text == "?"
                ) {
                    FormulaNode.Blank
                } else {
                    FormulaNode.Boxed(content)
                }
            }
            "text", "mathrm", "operatorname" -> {
                FormulaNode.Text(readRawGroup(), italic = false)
            }
            "vec" -> FormulaNode.Vector(parseGroup())
            "overline", "bar" -> FormulaNode.Overline(parseGroup())
            "left", "right" -> null
            "quad" -> FormulaNode.Space(1.0f)
            "qquad" -> FormulaNode.Space(2.0f)
            "sin", "cos", "tan", "cot", "sec", "csc", "ln", "log", "exp" -> {
                FormulaNode.Text(name, italic = false)
            }
            "Delta", "Phi", "Omega", "Gamma", "Theta", "Lambda", "Sigma" -> {
                FormulaNode.Text(GREEK.getValue(name), italic = false)
            }
            "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "theta",
            "lambda", "mu", "nu", "pi", "rho", "sigma", "tau", "phi", "varphi",
            "omega", "eta", "kappa", "chi", "psi", "zeta", "xi",
            -> FormulaNode.Text(GREEK.getValue(name), italic = true)
            "cdot" -> FormulaNode.Text("·", italic = false)
            "times" -> FormulaNode.Text("×", italic = false)
            "div" -> FormulaNode.Text("÷", italic = false)
            "pm" -> FormulaNode.Text("±", italic = false)
            "approx" -> FormulaNode.Text("≈", italic = false)
            "le", "leq" -> FormulaNode.Text("≤", italic = false)
            "ge", "geq" -> FormulaNode.Text("≥", italic = false)
            "ne", "neq" -> FormulaNode.Text("≠", italic = false)
            "to" -> FormulaNode.Text("→", italic = false)
            "infty" -> FormulaNode.Text("∞", italic = false)
            "degree" -> FormulaNode.Text("°", italic = false)
            else -> throw FormulaParseException("不支持的命令 \\$name", commandStart)
        }
    }

    private fun parseGroup(): List<FormulaNode> {
        skipWhitespace()
        if (position >= source.length || source[position] != '{') {
            return listOf(parseSingleAtom())
        }
        position++ // {
        val content = parseSequence(until = '}')
        if (position >= source.length || source[position] != '}') {
            throw FormulaParseException("缺少 }", position)
        }
        position++ // }
        return trimSpaces(content)
    }

    private fun parseSingleAtom(): FormulaNode {
        skipWhitespace()
        if (position >= source.length) return FormulaNode.Text("")
        return when (val char = source[position]) {
            '\\' -> parseCommand() ?: FormulaNode.Text("")
            '{' -> {
                val group = parseGroup()
                if (group.size == 1) group.first() else FormulaNode.Boxed(group)
            }
            else -> {
                position++
                if (char.isLetter()) {
                    FormulaNode.Text(char.toString(), italic = true)
                } else {
                    FormulaNode.Text(char.toString(), italic = false)
                }
            }
        }
    }

    private fun attachScript(nodes: MutableList<FormulaNode>, isSup: Boolean) {
        position++ // _ or ^
        skipWhitespace()
        val scriptNodes = if (position < source.length && source[position] == '{') {
            parseGroup()
        } else {
            listOf(parseSingleAtom())
        }
        val scriptText = flattenPlain(scriptNodes)
        val base = nodes.removeLastOrNull() ?: FormulaNode.Text("")
        if (base is FormulaNode.Text && scriptText != null) {
            nodes += if (isSup) {
                base.copy(sup = (base.sup.orEmpty() + scriptText).ifEmpty { scriptText })
            } else {
                base.copy(sub = (base.sub.orEmpty() + scriptText).ifEmpty { scriptText })
            }
        } else if (base is FormulaNode.Scripted) {
            nodes += if (isSup) {
                base.copy(sup = (base.sup.orEmpty() + scriptNodes))
            } else {
                base.copy(sub = (base.sub.orEmpty() + scriptNodes))
            }
        } else {
            nodes += if (isSup) {
                FormulaNode.Scripted(base = base, sup = scriptNodes)
            } else {
                FormulaNode.Scripted(base = base, sub = scriptNodes)
            }
        }
    }

    private fun readTextRun(): String {
        val start = position
        while (position < source.length) {
            val char = source[position]
            if (char.isWhitespace() || char == '\\' || char == '{' || char == '}' ||
                char == '_' || char == '^'
            ) {
                break
            }
            position++
        }
        if (position == start) {
            // Defensive: consume one unknown character instead of looping forever.
            position++
            return source[start].toString()
        }
        return source.substring(start, position)
    }

    private fun appendTextRun(nodes: MutableList<FormulaNode>, run: String) {
        var index = 0
        while (index < run.length) {
            val char = run[index]
            val italic = char.isLetter()
            var end = index + 1
            while (end < run.length && run[end].isLetter() == italic) end++
            appendText(nodes, run.substring(index, end), italic)
            index = end
        }
    }

    private fun appendText(nodes: MutableList<FormulaNode>, text: String, italic: Boolean) {
        if (text.isEmpty()) return
        val last = nodes.lastOrNull()
        if (last is FormulaNode.Text && last.italic == italic && last.sup == null && last.sub == null) {
            nodes[nodes.lastIndex] = last.copy(text = last.text + text)
        } else {
            nodes += FormulaNode.Text(text, italic = italic)
        }
    }

    private fun readRawGroup(): String {
        skipWhitespace()
        if (position >= source.length || source[position] != '{') {
            return if (position < source.length) source[position++].toString() else ""
        }
        position++
        val start = position
        var depth = 1
        while (position < source.length && depth > 0) {
            when (source[position]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) break
            position++
        }
        val value = source.substring(start, position)
        if (position < source.length && source[position] == '}') position++
        return value
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun trimSpaces(nodes: List<FormulaNode>): List<FormulaNode> {
        val result = nodes.toMutableList()
        while (result.firstOrNull() is FormulaNode.Space) result.removeAt(0)
        while (result.lastOrNull() is FormulaNode.Space) result.removeAt(result.lastIndex)
        return result
    }

    private fun flattenPlain(nodes: List<FormulaNode>): String? {
        val builder = StringBuilder()
        nodes.forEach { node ->
            if (node !is FormulaNode.Text || node.sup != null || node.sub != null) return null
            builder.append(node.text)
        }
        return builder.toString().ifEmpty { null }
    }

    companion object {
        private val GREEK = mapOf(
            "alpha" to "α",
            "beta" to "β",
            "gamma" to "γ",
            "delta" to "δ",
            "Delta" to "Δ",
            "epsilon" to "ε",
            "varepsilon" to "ε",
            "theta" to "θ",
            "lambda" to "λ",
            "mu" to "μ",
            "nu" to "ν",
            "pi" to "π",
            "rho" to "ρ",
            "sigma" to "σ",
            "tau" to "τ",
            "phi" to "φ",
            "varphi" to "φ",
            "Phi" to "Φ",
            "omega" to "ω",
            "Omega" to "Ω",
            "eta" to "η",
            "kappa" to "κ",
            "chi" to "χ",
            "psi" to "ψ",
            "zeta" to "ζ",
            "xi" to "ξ",
            "Gamma" to "Γ",
            "Theta" to "Θ",
            "Lambda" to "Λ",
            "Sigma" to "Σ",
        )
    }
}

fun formulaPlainText(latex: String): String {
    val nodes = runCatching { FormulaParser(latex).parse() }
        .getOrElse { return latex }
    return buildString { appendNodes(nodes) }
}

private fun StringBuilder.appendNodes(nodes: List<FormulaNode>) {
    nodes.forEach { node ->
        when (node) {
            is FormulaNode.Text -> {
                append(node.text)
                node.sub?.let { append("(").append(it).append(" 下标)") }
                node.sup?.let { append("(").append(it).append(" 次方)") }
            }
            is FormulaNode.Fraction -> {
                append("(")
                appendNodes(node.numerator)
                append(") 除以 (")
                appendNodes(node.denominator)
                append(")")
            }
            is FormulaNode.Radical -> {
                append("根号 (")
                appendNodes(node.content)
                append(")")
            }
            FormulaNode.Blank -> append("空格")
            is FormulaNode.Boxed -> appendNodes(node.content)
            is FormulaNode.Overline -> appendNodes(node.content)
            is FormulaNode.Vector -> appendNodes(node.content)
            is FormulaNode.Scripted -> {
                appendNodes(listOf(node.base))
                node.sub?.let {
                    append("(")
                    appendNodes(it)
                    append(" 下标)")
                }
                node.sup?.let {
                    append("(")
                    appendNodes(it)
                    append(" 次方)")
                }
            }
            is FormulaNode.Space -> append(" ")
        }
    }
}

internal fun maxNodeDepth(nodes: List<FormulaNode>, depth: Int = 1): Int {
    var maxDepth = depth
    nodes.forEach { node ->
        val childDepth = when (node) {
            is FormulaNode.Fraction -> max(
                maxNodeDepth(node.numerator, depth + 1),
                maxNodeDepth(node.denominator, depth + 1),
            )
            is FormulaNode.Radical -> maxNodeDepth(node.content, depth + 1)
            is FormulaNode.Boxed -> maxNodeDepth(node.content, depth + 1)
            is FormulaNode.Overline -> maxNodeDepth(node.content, depth + 1)
            is FormulaNode.Vector -> maxNodeDepth(node.content, depth + 1)
            is FormulaNode.Scripted -> listOf(
                maxNodeDepth(listOf(node.base), depth + 1),
                maxNodeDepth(node.sup.orEmpty(), depth + 1),
                maxNodeDepth(node.sub.orEmpty(), depth + 1),
            ).max()
            else -> depth
        }
        if (childDepth > maxDepth) maxDepth = childDepth
    }
    return maxDepth
}
