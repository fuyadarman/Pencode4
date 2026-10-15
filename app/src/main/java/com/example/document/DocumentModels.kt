package com.example.document

enum class DocumentType(val extension: String, val displayName: String, val mimeType: String) {
    PDF("pdf", "PDF Document", "application/pdf"),
    HTML("html", "Web HTML Document", "text/html"),
    MARKDOWN("md", "Markdown Document", "text/markdown"),
    PLAIN_TEXT("txt", "Plain Text", "text/plain"),
    CSV("csv", "CSV Spreadsheet", "text/csv"),
    JSON("json", "JSON Document", "application/json")
}

enum class DocumentTheme(
    val id: String,
    val displayName: String,
    val primaryColor: Long,
    val backgroundColor: Long,
    val textColor: Long,
    val accentColor: Long,
    val headerFontFamily: String
) {
    MODERN("modern", "Modern Indigo", 0xFF4F46E5, 0xFFFFFFFF, 0xFF1E293B, 0xFF06B6D4, "sans-serif"),
    ELEGANT("elegant", "Elegant Serif", 0xFF1E3A8A, 0xFFFAF9F6, 0xFF1C1917, 0xFFD97706, "serif"),
    MINIMAL("minimal", "Clean Minimal", 0xFF18181B, 0xFFFFFFFF, 0xFF27272A, 0xFF71717A, "sans-serif"),
    CYBERPUNK("cyberpunk", "Emerald Tech", 0xFF059669, 0xFFF0FDF4, 0xFF064E3B, 0xFF10B981, "monospace"),
    DARK_PROFESSIONAL("dark", "Dark Obsidian", 0xFF38BDF8, 0xFF0F172A, 0xFFF1F5F9, 0xFF818CF8, "sans-serif")
}

data class DocumentStyleConfig(
    val theme: DocumentTheme = DocumentTheme.MODERN,
    val fontSize: Int = 14,
    val titleFontSize: Int = 24,
    val pageMarginDp: Int = 36,
    val lineSpacingMultiplier: Float = 1.4f,
    val showPageNumbers: Boolean = true,
    val showHeader: Boolean = true,
    val showFooter: Boolean = true,
    val customHeaderTitle: String = "",
    val customFooterText: String = "",
    val author: String = "PenCode AI",
    val orientationLandscape: Boolean = false
)

data class GeneratedDocumentResult(
    val filePath: String,
    val fileName: String,
    val type: DocumentType,
    val sizeBytes: Long,
    val pageCount: Int = 1,
    val message: String = "Success"
)
