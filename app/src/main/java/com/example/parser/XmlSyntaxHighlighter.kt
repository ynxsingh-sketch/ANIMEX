package com.example.parser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

object XmlSyntaxHighlighter {

    fun highlight(text: String, errorLine: Int? = null): AnnotatedString {
        return buildAnnotatedString {
            append(text)

            // Pattern for XML comments <!-- ... -->
            val commentRegex = Regex("<!--[\\s\\S]*?-->")
            for (match in commentRegex.findAll(text)) {
                addStyle(
                    SpanStyle(color = SyntaxComment, fontFamily = FontFamily.Monospace),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Pattern for XML tags <... > or </... >
            val tagRegex = Regex("</?([a-zA-Z0-9_-]+)")
            for (match in tagRegex.findAll(text)) {
                val tagGroup = match.groups[1]
                if (tagGroup != null) {
                    addStyle(
                        SpanStyle(color = SyntaxTag, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        tagGroup.range.first,
                        tagGroup.range.last + 1
                    )
                }
            }

            // Pattern for attribute names attr=
            val attrRegex = Regex("\\s+([a-zA-Z0-9_-]+)(?=\\s*=)")
            for (match in attrRegex.findAll(text)) {
                val group = match.groups[1]
                if (group != null) {
                    addStyle(
                        SpanStyle(color = SyntaxAttr, fontFamily = FontFamily.Monospace),
                        group.range.first,
                        group.range.last + 1
                    )
                }
            }

            // Pattern for quoted attribute values "..."
            val stringRegex = Regex("\"([^\"]*)\"")
            for (match in stringRegex.findAll(text)) {
                addStyle(
                    SpanStyle(color = SyntaxString, fontFamily = FontFamily.Monospace),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Highlight error line with red background if present
            if (errorLine != null && errorLine > 0) {
                val lines = text.split("\n")
                if (errorLine <= lines.size) {
                    var startIndex = 0
                    for (i in 0 until (errorLine - 1)) {
                        startIndex += lines[i].length + 1
                    }
                    val endIndex = (startIndex + lines[errorLine - 1].length).coerceAtMost(text.length)
                    addStyle(
                        SpanStyle(background = Color(0x33EF4444)),
                        startIndex,
                        endIndex
                    )
                }
            }
        }
    }
}
