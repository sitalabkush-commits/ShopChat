package com.shopmanager.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

sealed interface Block {
    data class Text(val s: String) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

private val SEPARATOR = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")
private val NUMERIC = Regex("""^[₹+\-]?[\d,]+(\.\d+)?%?$""")

private fun cells(line: String) = line.trim().trim('|').split('|').map { it.trim() }

fun parseBlocks(src: String): List<Block> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = mutableListOf<Block>()
    val text = StringBuilder()
    fun flush() {
        if (text.isNotBlank()) out += Block.Text(text.toString().trim('\n'))
        text.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val isTable = line.trim().startsWith("|") && i + 1 < lines.size && SEPARATOR.matches(lines[i + 1].trim())
        if (isTable) {
            flush()
            val header = cells(line)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) { rows += cells(lines[i]); i++ }
            out += Block.Table(header, rows)
        } else {
            text.append(line).append('\n'); i++
        }
    }
    flush()
    return out
}

private fun boldText(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { idx, part ->
        if (idx % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

/** Plain text with **bold**, and Markdown tables. */
@Composable
fun MarkdownText(text: String) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Text -> Text(boldText(b.s), style = MaterialTheme.typography.bodyLarge)
                is Block.Table -> TableView(b)
            }
        }
    }
}

@Composable
private fun TableView(t: Block.Table) {
    val cs = MaterialTheme.colorScheme
    val cols = t.header.size
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Row(Modifier.background(cs.secondaryContainer)) {
            t.header.forEach { h ->
                Text(
                    boldText(h), fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(112.dp).padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        t.rows.forEach { row ->
            HorizontalDivider(color = cs.outlineVariant)
            Row {
                for (c in 0 until cols) {
                    val cell = row.getOrElse(c) { "" }
                    Text(
                        boldText(cell),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = if (NUMERIC.matches(cell)) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.width(112.dp).padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
