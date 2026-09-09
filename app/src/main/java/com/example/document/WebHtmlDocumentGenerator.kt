package com.example.document

import android.content.Context
import java.io.File

object WebHtmlDocumentGenerator {

    fun generateHtml(
        title: String,
        content: String,
        style: DocumentStyleConfig,
        outputFile: File
    ): GeneratedDocumentResult {
        val primaryHex = String.format("#%06X", 0xFFFFFF and style.theme.primaryColor.toInt())
        val bgHex = String.format("#%06X", 0xFFFFFF and style.theme.backgroundColor.toInt())
        val textHex = String.format("#%06X", 0xFFFFFF and style.theme.textColor.toInt())
        val accentHex = String.format("#%06X", 0xFFFFFF and style.theme.accentColor.toInt())

        val htmlBody = parseMarkdownToHtml(content)

        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${title.replace("<", "&lt;").replace(">", "&gt;")}</title>
                <style>
                    @page {
                        size: ${if (style.orientationLandscape) "A4 landscape" else "A4 portrait"};
                        margin: ${style.pageMarginDp}px;
                    }
                    body {
                        font-family: ${if (style.theme.headerFontFamily == "serif") "Georgia, serif" else if (style.theme.headerFontFamily == "monospace") "'Fira Code', monospace" else "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"};
                        background-color: $bgHex;
                        color: $textHex;
                        font-size: ${style.fontSize}px;
                        line-height: ${style.lineSpacingMultiplier};
                        margin: 0;
                        padding: ${style.pageMarginDp}px;
                    }
                    .header {
                        display: flex;
                        justify-content: space-between;
                        border-bottom: 1px solid rgba(0,0,0,0.1);
                        padding-bottom: 8px;
                        margin-bottom: 24px;
                        font-size: 11px;
                        color: $accentHex;
                    }
                    h1.doc-title {
                        color: $primaryHex;
                        font-size: ${style.titleFontSize}px;
                        margin-top: 0;
                        margin-bottom: 6px;
                    }
                    .meta {
                        color: #64748b;
                        font-size: 12px;
                        margin-bottom: 24px;
                    }
                    h2 {
                        color: $primaryHex;
                        border-bottom: 2px solid $primaryHex;
                        padding-bottom: 4px;
                        margin-top: 24px;
                    }
                    h3 {
                        color: $accentHex;
                        margin-top: 18px;
                    }
                    code {
                        background: rgba(0,0,0,0.05);
                        padding: 2px 5px;
                        border-radius: 4px;
                        font-family: monospace;
                    }
                    pre {
                        background: rgba(0,0,0,0.06);
                        padding: 12px;
                        border-radius: 8px;
                        overflow-x: auto;
                    }
                    blockquote {
                        border-left: 4px solid $primaryHex;
                        margin: 0;
                        padding-left: 16px;
                        color: #475569;
                    }
                    .footer {
                        margin-top: 40px;
                        border-top: 1px solid rgba(0,0,0,0.1);
                        padding-top: 12px;
                        font-size: 11px;
                        color: #94a3b8;
                        display: flex;
                        justify-content: space-between;
                    }
                </style>
            </head>
            <body>
                ${if (style.showHeader) """<div class="header"><span>${style.customHeaderTitle.ifBlank { title }}</span><span>PenCode Generated</span></div>""" else ""}
                <h1 class="doc-title">${title.replace("<", "&lt;").replace(">", "&gt;")}</h1>
                <div class="meta">Author: ${style.author}</div>
                <div class="content">
                    $htmlBody
                </div>
                ${if (style.showFooter) """<div class="footer"><span>${style.customFooterText.ifBlank { "Document generated via PenCode AI" }}</span><span>Page 1</span></div>""" else ""}
            </body>
            </html>
        """.trimIndent()

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(htmlContent, Charsets.UTF_8)

        return GeneratedDocumentResult(
            filePath = outputFile.absolutePath,
            fileName = outputFile.name,
            type = DocumentType.HTML,
            sizeBytes = outputFile.length(),
            message = "HTML Document successfully generated"
        )
    }

    private fun parseMarkdownToHtml(markdown: String): String {
        val lines = markdown.split("\n")
        val sb = StringBuilder()
        var inList = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("# ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h2>").append(line.removePrefix("# ").trim()).append("</h2>\n")
            } else if (line.startsWith("## ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h3>").append(line.removePrefix("## ").trim()).append("</h3>\n")
            } else if (line.startsWith("- ") || line.startsWith("• ") || line.startsWith("* ")) {
                if (!inList) {
                    sb.append("<ul>\n")
                    inList = true
                }
                sb.append("<li>").append(line.substring(2).trim()).append("</li>\n")
            } else if (line.isBlank()) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<br/>\n")
            } else {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<p>").append(line).append("</p>\n")
            }
        }
        if (inList) sb.append("</ul>\n")
        return sb.toString()
    }
}
