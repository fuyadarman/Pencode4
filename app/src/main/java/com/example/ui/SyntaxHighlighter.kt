package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class SyntaxHighlighter(private val isDark: Boolean = true) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val inputText = text.text
        val keywordColor = if (isDark) Color(0xFFFF7B72) else Color(0xFFCF222E)
        val stringColor = if (isDark) Color(0xFFA5D6FF) else Color(0xFF0A3069)
        val commentColor = if (isDark) Color(0xFF8B949E) else Color(0xFF6E7781)

        val annotatedString = buildAnnotatedString {
            append(inputText)
            
            // Keywords
            val keywordPattern = "\\b(package|import|class|fun|val|var|if|else|when|return|true|false|null|for|while|do|break|continue|interface|object|typealias|enum|modifier)\\b".toRegex()
            keywordPattern.findAll(inputText).forEach {
                addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1)
            }
            
            // Strings
            val stringPattern = "\"[^\"]*\"".toRegex()
            stringPattern.findAll(inputText).forEach {
                addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
            }
            
            // Comments
            val commentPattern = "//.*".toRegex()
            commentPattern.findAll(inputText).forEach {
                addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
