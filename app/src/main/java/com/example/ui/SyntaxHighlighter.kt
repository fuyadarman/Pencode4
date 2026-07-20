package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class SyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val inputText = text.text
        val annotatedString = buildAnnotatedString {
            append(inputText)
            
            // Keywords
            val keywordPattern = "\\b(package|import|class|fun|val|var|if|else|when|return|true|false|null|for|while|do|break|continue|interface|object|typealias|enum|modifier)\\b".toRegex()
            keywordPattern.findAll(inputText).forEach {
                addStyle(SpanStyle(color = Color(0xFFFF7B72)), it.range.first, it.range.last + 1)
            }
            
            // Strings
            val stringPattern = "\"[^\"]*\"".toRegex()
            stringPattern.findAll(inputText).forEach {
                addStyle(SpanStyle(color = Color(0xFFA5D6FF)), it.range.first, it.range.last + 1)
            }
            
            // Comments
            val commentPattern = "//.*".toRegex()
            commentPattern.findAll(inputText).forEach {
                addStyle(SpanStyle(color = Color(0xFF8B949E)), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
