package com.example.document

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NativePdfGenerator {

    fun generatePdf(
        context: Context,
        title: String,
        content: String,
        style: DocumentStyleConfig,
        outputFile: File
    ): GeneratedDocumentResult {
        val pdfDoc = PdfDocument()

        val pageWidth = if (style.orientationLandscape) 842 else 595
        val pageHeight = if (style.orientationLandscape) 595 else 842

        val margin = (style.pageMarginDp * 1.5f).toInt()
        val printableWidth = pageWidth - (margin * 2)

        val titlePaint = Paint().apply {
            color = style.theme.primaryColor.toInt()
            textSize = style.titleFontSize.toFloat()
            isAntiAlias = true
            typeface = Typeface.create(
                if (style.theme.headerFontFamily == "serif") Typeface.SERIF
                else if (style.theme.headerFontFamily == "monospace") Typeface.MONOSPACE
                else Typeface.SANS_SERIF,
                Typeface.BOLD
            )
        }

        val bodyPaint = Paint().apply {
            color = style.theme.textColor.toInt()
            textSize = style.fontSize.toFloat()
            isAntiAlias = true
            typeface = Typeface.create(
                if (style.theme.headerFontFamily == "monospace") Typeface.MONOSPACE
                else Typeface.SANS_SERIF,
                Typeface.NORMAL
            )
        }

        val metaPaint = Paint().apply {
            color = (style.theme.accentColor and 0x00FFFFFF or 0x99000000).toInt()
            textSize = 10f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val bgPaint = Paint().apply {
            color = style.theme.backgroundColor.toInt()
        }

        val lineSpacing = bodyPaint.textSize * style.lineSpacingMultiplier
        val lines = mutableListOf<String>()

        val rawParagraphs = content.split("\n")
        for (paragraph in rawParagraphs) {
            if (paragraph.isBlank()) {
                lines.add("")
                continue
            }

            val words = paragraph.split(" ")
            var currentLine = StringBuilder()
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val measuredWidth = bodyPaint.measureText(testLine)
                if (measuredWidth <= printableWidth) {
                    currentLine = StringBuilder(testLine)
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                    }
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }

        var currentPageNumber = 1
        var lineIndex = 0
        val totalLines = lines.size

        val headerHeight = if (style.showHeader) 40 else 0
        val footerHeight = if (style.showFooter || style.showPageNumbers) 35 else 0

        while (lineIndex < totalLines || currentPageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            // Fill background
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

            var yPos = margin.toFloat()

            // Header
            if (style.showHeader) {
                val headerText = if (style.customHeaderTitle.isNotBlank()) style.customHeaderTitle else title
                canvas.drawText(headerText, margin.toFloat(), yPos + 15, metaPaint)
                canvas.drawLine(
                    margin.toFloat(),
                    yPos + 22,
                    (pageWidth - margin).toFloat(),
                    yPos + 22,
                    metaPaint
                )
                yPos += headerHeight
            }

            // Title on first page
            if (currentPageNumber == 1) {
                canvas.drawText(title, margin.toFloat(), yPos + titlePaint.textSize, titlePaint)
                yPos += titlePaint.textSize + 8

                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                val authorInfo = if (style.author.isNotBlank()) "By ${style.author} • $dateStr" else dateStr
                canvas.drawText(authorInfo, margin.toFloat(), yPos + 10, metaPaint)
                yPos += 28

                val dividerPaint = Paint().apply {
                    color = style.theme.primaryColor.toInt()
                    strokeWidth = 2f
                }
                canvas.drawLine(
                    margin.toFloat(),
                    yPos,
                    (margin + 60).toFloat(),
                    yPos,
                    dividerPaint
                )
                yPos += 18
            }

            val maxContentY = pageHeight - margin - footerHeight

            while (lineIndex < totalLines && (yPos + lineSpacing) < maxContentY) {
                val textLine = lines[lineIndex]
                if (textLine.isNotBlank()) {
                    if (textLine.startsWith("# ")) {
                        val headingPaint = Paint(titlePaint).apply { textSize = style.titleFontSize * 0.85f }
                        canvas.drawText(textLine.removePrefix("# "), margin.toFloat(), yPos + headingPaint.textSize, headingPaint)
                        yPos += headingPaint.textSize + 6
                    } else if (textLine.startsWith("## ")) {
                        val subHeadingPaint = Paint(titlePaint).apply { textSize = style.titleFontSize * 0.72f }
                        canvas.drawText(textLine.removePrefix("## "), margin.toFloat(), yPos + subHeadingPaint.textSize, subHeadingPaint)
                        yPos += subHeadingPaint.textSize + 4
                    } else if (textLine.startsWith("• ") || textLine.startsWith("- ")) {
                        canvas.drawCircle((margin + 4).toFloat(), yPos + (bodyPaint.textSize / 2), 2.5f, titlePaint)
                        canvas.drawText(textLine.substring(2), (margin + 14).toFloat(), yPos + bodyPaint.textSize, bodyPaint)
                        yPos += lineSpacing
                    } else {
                        canvas.drawText(textLine, margin.toFloat(), yPos + bodyPaint.textSize, bodyPaint)
                        yPos += lineSpacing
                    }
                } else {
                    yPos += lineSpacing * 0.6f
                }
                lineIndex++
            }

            // Footer
            if (style.showFooter || style.showPageNumbers) {
                val footerY = pageHeight - margin + 15
                canvas.drawLine(
                    margin.toFloat(),
                    (footerY - 18).toFloat(),
                    (pageWidth - margin).toFloat(),
                    (footerY - 18).toFloat(),
                    metaPaint
                )
                if (style.showFooter) {
                    val footerNotice = if (style.customFooterText.isNotBlank()) style.customFooterText else "Generated via PenCode"
                    canvas.drawText(footerNotice, margin.toFloat(), footerY.toFloat(), metaPaint)
                }
                if (style.showPageNumbers) {
                    val pageStr = "Page $currentPageNumber"
                    val pageStrWidth = metaPaint.measureText(pageStr)
                    canvas.drawText(pageStr, pageWidth - margin - pageStrWidth, footerY.toFloat(), metaPaint)
                }
            }

            pdfDoc.finishPage(page)
            currentPageNumber++
        }

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        return GeneratedDocumentResult(
            filePath = outputFile.absolutePath,
            fileName = outputFile.name,
            type = DocumentType.PDF,
            sizeBytes = outputFile.length(),
            pageCount = currentPageNumber - 1,
            message = "PDF successfully generated"
        )
    }
}
