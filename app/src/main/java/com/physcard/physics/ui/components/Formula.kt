package com.physcard.physics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.physcard.physics.ui.theme.MathFont

/**
 * Renders the app's TeX subset with text and Compose layout primitives: crisp at every density,
 * selectable by screen readers, and fully offline. Fractions get a real rule, square roots get a
 * vinculum, and sub/superscripts use baseline shifts instead of hard-coded offsets.
 */
@Composable
fun Formula(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
) {
    val nodes = remember(latex) {
        runCatching { FormulaParser(latex).parse() }
            .getOrElse { listOf(FormulaNode.Text(latex, italic = false)) }
    }
    val description = remember(latex, contentDescription) {
        contentDescription ?: formulaPlainText(latex)
    }
    Box(
        modifier = modifier.clearAndSetSemantics {
            this.contentDescription = description
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        FormulaSequence(nodes, fontSize, color)
    }
}

@Composable
private fun FormulaSequence(
    nodes: List<FormulaNode>,
    fontSize: TextUnit,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        nodes.forEach { node ->
            FormulaNodeView(node, fontSize, color)
        }
    }
}

@Composable
private fun FormulaNodeView(node: FormulaNode, fontSize: TextUnit, color: Color) {
    when (node) {
        is FormulaNode.Text -> FormulaTextNode(node, fontSize, color)
        is FormulaNode.Fraction -> FractionNode(node, fontSize, color)
        is FormulaNode.Radical -> RadicalNode(node, fontSize, color)
        FormulaNode.Blank -> BlankNode(fontSize, color)
        is FormulaNode.Boxed -> BoxedNode(node, fontSize, color)
        is FormulaNode.Overline -> OverlineNode(node, fontSize, color)
        is FormulaNode.Vector -> VectorNode(node, fontSize, color)
        is FormulaNode.Scripted -> ScriptedNode(node, fontSize, color)
        is FormulaNode.Space -> Spacer(Modifier.width((fontSize.value * node.widthEm).dp))
    }
}

@Composable
private fun FormulaTextNode(node: FormulaNode.Text, fontSize: TextUnit, color: Color) {
    val annotated = buildAnnotatedString {
        append(node.text)
        node.sub?.let { subscript ->
            withStyle(
                SpanStyle(
                    fontSize = fontSize * 0.66f,
                    baselineShift = BaselineShift(-0.18f),
                    fontStyle = FontStyle.Normal,
                ),
            ) {
                append(subscript)
            }
        }
        node.sup?.let { superscript ->
            withStyle(
                SpanStyle(
                    fontSize = fontSize * 0.66f,
                    baselineShift = BaselineShift(0.34f),
                    fontStyle = FontStyle.Normal,
                ),
            ) {
                append(superscript)
            }
        }
    }
    Text(
        text = annotated,
        color = color,
        style = TextStyle(
            fontFamily = MathFont,
            fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = fontSize,
            lineHeight = fontSize * 1.0f,
            letterSpacing = 0.sp,
        ),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun FractionNode(node: FormulaNode.Fraction, fontSize: TextUnit, color: Color) {
    val childSize = fontSize * 0.78f
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FormulaSequence(node.numerator, childSize, color)
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(color.copy(alpha = 0.62f)),
        )
        FormulaSequence(node.denominator, childSize, color)
    }
}

@Composable
private fun RadicalNode(node: FormulaNode.Radical, fontSize: TextUnit, color: Color) {
    val radicalWidth = (fontSize.value * 0.58f).dp
    val topPadding = (fontSize.value * 0.28f).dp
    val trailingScript = node.content.lastOrNull().let { last ->
        last is FormulaNode.Text && (last.sup != null || last.sub != null)
    }
    val trailingPad = if (trailingScript) (fontSize.value * 0.42f).dp else 0.dp
    Box(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .drawBehind {
                val stroke = 1.25.dp.toPx()
                val width = radicalWidth.toPx()
                val top = 1.dp.toPx()
                val bottom = size.height - 1.dp.toPx()
                drawLine(
                    color = color.copy(alpha = 0.68f),
                    start = Offset(0f, size.height * 0.56f),
                    end = Offset(width * 0.52f, bottom),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color.copy(alpha = 0.68f),
                    start = Offset(width * 0.52f, bottom),
                    end = Offset(width, top),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color.copy(alpha = 0.68f),
                    start = Offset(width, top),
                    end = Offset(size.width, top),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            },
    ) {
        Box(
            modifier = Modifier.padding(start = radicalWidth, top = topPadding, end = trailingPad),
        ) {
            FormulaSequence(node.content, fontSize * 0.96f, color)
        }
    }
}

@Composable
private fun BlankNode(fontSize: TextUnit, color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .border(
                width = 1.2.dp,
                color = color.copy(alpha = 0.55f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            color = color,
            style = TextStyle(
                fontFamily = MathFont,
                fontSize = fontSize * 0.82f,
                fontStyle = FontStyle.Italic,
                lineHeight = fontSize * 0.95f,
            ),
        )
    }
}

@Composable
private fun BoxedNode(node: FormulaNode.Boxed, fontSize: TextUnit, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        FormulaSequence(node.content, fontSize * 0.9f, color)
    }
}

@Composable
private fun OverlineNode(node: FormulaNode.Overline, fontSize: TextUnit, color: Color) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(color.copy(alpha = 0.62f)),
        )
        FormulaSequence(node.content, fontSize, color)
    }
}

@Composable
private fun VectorNode(node: FormulaNode.Vector, fontSize: TextUnit, color: Color) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "→",
            color = color,
            style = TextStyle(
                fontFamily = MathFont,
                fontSize = fontSize * 0.62f,
                lineHeight = fontSize * 0.55f,
            ),
            maxLines = 1,
        )
        FormulaSequence(node.content, fontSize, color)
    }
}

@Composable
private fun ScriptedNode(node: FormulaNode.Scripted, fontSize: TextUnit, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        FormulaNodeView(node.base, fontSize, color)
        Column {
            node.sup?.let { FormulaSequence(it, fontSize * 0.64f, color) }
            node.sub?.let { FormulaSequence(it, fontSize * 0.64f, color) }
        }
    }
}
