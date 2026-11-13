package com.nofilter.ai.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.AbsoluteSizeSpan
import com.nofilter.ai.ui.Ui

/** Very small markdown renderer -> Spannable (headings, bold, italic, code, links, lists, quotes, fences). */
object Markdown {

    fun render(md: String, p: Ui.Palette, baseSizePx: Int): CharSequence {
        val out = SpannableStringBuilder()
        val lines = md.split("\n")
        var inCode = false
        val codeBuf = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    appendCode(out, codeBuf.toString(), p, baseSizePx)
                    codeBuf.setLength(0)
                    inCode = false
                } else {
                    inCode = true
                }
                i++
                continue
            }
            if (inCode) {
                codeBuf.append(line).append("\n")
                i++
                continue
            }
            appendLine(out, line, p, baseSizePx)
            if (i < lines.size - 1) out.append("\n")
            i++
        }
        if (inCode && codeBuf.isNotEmpty()) appendCode(out, codeBuf.toString(), p, baseSizePx)
        return out
    }

    private fun appendCode(out: SpannableStringBuilder, code: String, p: Ui.Palette, baseSizePx: Int) {
        val start = out.length
        out.append(code.trimEnd('\n'))
        val end = out.length
        if (end > start) {
            out.setSpan(BackgroundColorSpan(p.codeBg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(p.accent2), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(AbsoluteSizeSpan((baseSizePx * 0.92f).toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        out.append("\n")
    }

    private fun appendLine(out: SpannableStringBuilder, line: String, p: Ui.Palette, baseSizePx: Int) {
        var text = line
        var sizeMul = 1.0f
        var boldAll = false
        var quote = false
        val h = Regex("^(#{1,6})\\s+(.*)$").find(text)
        if (h != null) {
            sizeMul = 1.5f - h.groupValues[1].length * 0.09f
            boldAll = true
            text = h.groupValues[2]
        } else if (text.trimStart().startsWith(">")) {
            quote = true
            text = text.trimStart().removePrefix(">").trimStart()
        } else if (Regex("^\\s*[-*•]\\s+").containsMatchIn(text)) {
            text = text.replaceFirst(Regex("^\\s*[-*•]\\s+"), "• ")
        }
        val start = out.length
        out.append(text)
        val end = out.length
        if (end == start) return
        if (boldAll) out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (sizeMul != 1.0f) out.setSpan(RelativeSizeSpan(sizeMul), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (quote) {
            out.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(p.muted), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        applyInline(out, start, end, p)
    }

    private fun applyInline(out: SpannableStringBuilder, start: Int, end: Int, p: Ui.Palette) {
        val s = out.subSequence(start, end).toString()
        // inline code
        applyPattern(out, start, s, Regex("`([^`]+)`")) { g ->
            listOf(TypefaceSpan("monospace"), ForegroundColorSpan(p.accent2))
        }
        // links [text](url)
        applyPattern(out, start, out.subSequence(start, end).toString(), Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")) { g ->
            listOf(URLSpan(g.groupValues[2]), ForegroundColorSpan(p.accent2), android.text.style.UnderlineSpan())
        }
        // bold **text** or __text__
        val s2 = out.subSequence(start, end).toString()
        applyPattern(out, start, s2, Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) { g ->
            listOf(StyleSpan(Typeface.BOLD))
        }
        // italic *text* or _text_
        val s3 = out.subSequence(start, end).toString()
        applyPattern(out, start, s3, Regex("(?<!\\w)\\*([^*\\n]+)\\*(?!\\w)|(?<![\\w])_([^_\\n]+)_(?![\\w])")) { g ->
            listOf(StyleSpan(Typeface.ITALIC))
        }
        // strikethrough ~~text~~
        val s4 = out.subSequence(start, end).toString()
        applyPattern(out, start, s4, Regex("~~([^~]+)~~")) { g ->
            listOf(StrikethroughSpan())
        }
    }

    private fun applyPattern(
        out: SpannableStringBuilder, base: Int, s: String,
        regex: Regex, spansFor: (MatchResult) -> List<Any>
    ) {
        for (m in regex.findAll(s)) {
            val innerStart = base + m.range.first + (if (m.groupValues.size > 1 && m.groupValues[1].isNotEmpty() && regex.pattern.contains("[")) 1 else 0)
            // compute inner group properly: use the last non-empty capture as content
            val contentGroup = (1 until m.groupValues.size).map { m.groupValues[it] }.firstOrNull { it.isNotEmpty() } ?: m.value
            val contentStart = base + m.value.indexOf(contentGroup)
            val contentEnd = contentStart + contentGroup.length
            for (span in spansFor(m)) {
                out.setSpan(span, contentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
