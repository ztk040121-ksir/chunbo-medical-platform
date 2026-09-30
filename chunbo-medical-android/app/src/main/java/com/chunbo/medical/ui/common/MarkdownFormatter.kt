package com.chunbo.medical.ui.common

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.chunbo.medical.R
import java.util.regex.Pattern

/**
 * 专为春播万象移动端打造的高性能医疗 Markdown 与富文本排版器
 * 将大模型输出的原始 Markdown 符号（###、**、列表、代码块）精细化转换为极具品质感、呼吸感和医学清晰度的富文本。
 * 颜色统一取自 colors 资源，自动适配深浅色主题。
 */
object MarkdownFormatter {

    private val BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*")

    fun format(rawText: String?, context: Context): CharSequence {
        if (rawText.isNullOrBlank()) return ""

        val headingColor = context.getColor(R.color.primary)
        val bulletColor = context.getColor(R.color.primary)
        val numColor = context.getColor(R.color.status_blue)
        val dividerColor = context.getColor(R.color.divider)

        // 行内 `code`/术语标签：去除反引号保留内容
        val cleaned = rawText.replace("`", "")

        val lines = cleaned.lines()
        val ssb = SpannableStringBuilder()

        var isFirstLine = true

        for (rawLine in lines) {
            val line = rawLine.trimEnd()

            if (!isFirstLine) {
                ssb.append("\n")
            }
            isFirstLine = false

            val trimmed = line.trimStart()

            // 1. 处理三级/二级/一级标题：### 标题 或 ## 标题
            if (trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ") || trimmed.startsWith("#### ")) {
                if (ssb.length > 1 && !ssb.endsWith("\n\n")) {
                    ssb.append("\n")
                }
                val headerText = trimmed.replace(Regex("^#+\\s*"), "")
                val start = ssb.length
                ssb.append(headerText)
                val end = ssb.length

                ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(RelativeSizeSpan(1.10f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(headingColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }

            // 2. 处理无序列表：- 文本 或 * 文本
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                val bulletContent = trimmed.substring(2).trimStart()
                val bulletStart = ssb.length
                ssb.append("•  ")
                ssb.setSpan(StyleSpan(Typeface.BOLD), bulletStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(bulletColor), bulletStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                appendWithInlineFormatting(ssb, bulletContent)
                continue
            }

            // 3. 处理有序列表：1. 文本, 2. 文本
            val numMatch = Regex("^(\\d+\\.)\\s+(.+)").find(trimmed)
            if (numMatch != null) {
                val numPrefix = numMatch.groupValues[1] + " "
                val itemText = numMatch.groupValues[2]

                val numStart = ssb.length
                ssb.append(numPrefix)
                ssb.setSpan(StyleSpan(Typeface.BOLD), numStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(numColor), numStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                appendWithInlineFormatting(ssb, itemText)
                continue
            }

            // 4. 处理 Markdown 表格对齐分隔符：| :--- | :--- | 等
            if (trimmed.startsWith("|") && (trimmed.contains("---") || trimmed.contains(":---"))) {
                continue // 直接跳过无意义的表格对齐虚线
            }

            // 5. 处理 Markdown 表格数据行：| col1 | col2 | ... |
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val cells = trimmed.split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                // 过滤纯表头行
                if (cells.any { it.contains("药品编号") || it.contains("通用名称") || it.contains("表头") || it.contains("操作") }) {
                    continue
                }

                // 结构化重组：如果有 2 个以上单元格，组合为可读列表行
                if (cells.size >= 2) {
                    val title = cells.getOrNull(1)?.replace("**", "") ?: cells[0]
                    val spec = cells.getOrNull(2)?.takeIf { !it.startsWith("MED") && !it.contains("¥") } ?: ""
                    val otherInfo = cells.drop(3).filter { it.isNotBlank() && !it.contains("MED-") }.take(3).joinToString(" · ")

                    val itemStr = buildString {
                        append("•  ").append(title)
                        if (spec.isNotBlank()) append(" (").append(spec).append(")")
                        if (otherInfo.isNotBlank()) append(" · ").append(otherInfo)
                    }

                    val bulletStart = ssb.length
                    ssb.append("•  ")
                    ssb.setSpan(StyleSpan(Typeface.BOLD), bulletStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(bulletColor), bulletStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    appendWithInlineFormatting(ssb, itemStr.removePrefix("•  "))
                    continue
                }
            }

            // 6. 处理分割线：--- 或 ***
            if (trimmed.matches(Regex("^[-*_]{3,}$"))) {
                val divStart = ssb.length
                ssb.append("────────────")
                ssb.setSpan(ForegroundColorSpan(dividerColor), divStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }

            // 7. 普通文本行（支持行内 **加粗** 与 `代码/标签`）
            appendWithInlineFormatting(ssb, line)
        }

        return ssb
    }

    /**
     * 处理行内加粗 **bold** 与标签 `tag`
     */
    private fun appendWithInlineFormatting(ssb: SpannableStringBuilder, text: String) {
        if (text.isEmpty()) return

        // 统一把 **加粗** 与 `code` 识别并添加 Span
        var cursor = 0
        val matcher = BOLD_PATTERN.matcher(text)

        while (matcher.find()) {
            val matchStart = matcher.start()
            val matchEnd = matcher.end()

            // 追加加粗之前的普通文字
            if (matchStart > cursor) {
                ssb.append(text.substring(cursor, matchStart))
            }

            val boldContent = matcher.group(1) ?: ""
            val spanStart = ssb.length
            ssb.append(boldContent)
            val spanEnd = ssb.length

            ssb.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            cursor = matchEnd
        }

        // 追加剩余普通文字
        if (cursor < text.length) {
            ssb.append(text.substring(cursor))
        }
    }
}
