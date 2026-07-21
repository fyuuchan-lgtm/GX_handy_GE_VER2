package com.example.yakuzaiapp.domain.audit

import android.util.Log
import com.google.mlkit.vision.text.Text

private const val TAG = "AuditParser"

data class DetectedDrugLine(
    val name: String,
    val sourceLines: List<String>,
    val quantityText: String? = null
)

data class OcrBlockForParsing(
    val text: String,
    val bounds: OcrBounds?
)

data class OcrBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

private fun android.graphics.Rect.toOcrBounds(): OcrBounds {
    return OcrBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}

object DocumentOcrParser {
    private val dosageKeywords = listOf(
        "錠",
        "カプセル",
        "錠剤",
        "散",
        "液",
        "軟膏",
        "クリーム",
        "ゲル",
        "坐剤",
        "坐削",
        "生剤",
        "生品",
        "学剤",
        "注",
        "吸入",
        "点眼",
        "貼付",
        "テープ",
        "パッチ",
        "シロップ",
        "ドライシロップ"
    )
    private val PACKAGING_UNITS = listOf(
        "錠",
        "カプセル",
        "包",
        "本",
        "個",
        "枚",
        "シート",
        "袋",
        "瓶",
        "管",
        "筒",
        "アンプル",
        "バイアル",
        "キット",
        "セット",
        "束",
        "巻",
        "mL",
        "g",
        "mg"
    )
    private val strengthPattern = Regex(
        """[0-9０-９]+(?:[.．][0-9０-９]+)?\s*(?:mg|ｍｇ|μg|μｇ|ug|ｕｇ|g|％|%|mL|ｍL|ml|IU|単位)""",
        RegexOption.IGNORE_CASE
    )
    private val dosageFormPattern = Regex(dosageKeywords.joinToString("|") { Regex.escape(it) })
    private val packagingUnitPattern = PACKAGING_UNITS.joinToString("|") { Regex.escape(it) }
    private val shelfCodePattern = Regex("""^[A-Z0-9]{1,4}$""")
    private val pureNumberPattern = Regex("""^[0-9０-９]+$""")
    private val packagingUnitOnlyPattern = Regex("""^(?:$packagingUnitPattern)$""", RegexOption.IGNORE_CASE)
    private val quantityOnlyPattern = Regex(
        """^[0-9０-９]+\s*(?:$packagingUnitPattern)$""",
        RegexOption.IGNORE_CASE
    )
    private val dateTimePattern = Regex(
        """([0-9０-９]{1,2}月[0-9０-９]{1,2}日|[0-9０-９]{1,2}:[0-9０-９]{2})"""
    )
    private val quantityUnits = listOf(
        "錠",
        "カプセル",
        "包",
        "本",
        "個",
        "枚",
        "シート",
        "袋",
        "瓶",
        "管",
        "筒",
        "アンプル",
        "バイアル",
        "キット",
        "セット",
        "束",
        "巻",
        "回"
    )
    private val quantityUnitPattern = quantityUnits.joinToString("|") { Regex.escape(it) }
    private val quantityPattern = Regex(
        """[ 　]*([0-9０-９]+(?:[.．][0-9０-９]+)?)\s*($quantityUnitPattern)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: Text): List<DetectedDrugLine> {
        val lines = text.textBlocks.flatMap { block -> block.lines }.map { line ->
            OcrBlockForParsing(
                text = line.text,
                bounds = line.boundingBox?.toOcrBounds()
            )
        }
        return parseLines(lines)
    }

    internal fun parseBlocks(blocks: List<OcrBlockForParsing>): List<DetectedDrugLine> {
        return parseLines(
            blocks.flatMap { block ->
                block.text
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { lineText -> block.copy(text = lineText) }
            }
        )
    }

    private fun parseLines(lines: List<OcrBlockForParsing>): List<DetectedDrugLine> {
        val result = lines
            .mapNotNull { line -> parseLine(line.text) }
            .distinctBy { "${it.name}|${it.quantityText.orEmpty()}" }

        Log.d(TAG, "detected drug names=${result.size}")
        return result
    }

    private fun parseLine(rawText: String): DetectedDrugLine? {
        val text = rawText.trim()
        if (text.isBlank()) return null
        if (shouldExclude(text)) return null
        val (normalizedName, quantityText) = extractDrugNameAndQuantity(text)
        if (!isDrugNameCandidate(normalizedName)) return null

        return DetectedDrugLine(
            name = normalizedName,
            quantityText = quantityText,
            sourceLines = listOf(text)
        )
    }

    private fun extractDrugNameAndQuantity(text: String): Pair<String, String?> {
        val match = quantityPattern.find(text)
        if (match == null) {
            return text to null
        }

        val number = toHalfWidthDigits(match.groupValues[1].trim())
        val unit = match.groupValues[2]
        val quantityText = "$number$unit"
        val name = text.substring(0, match.range.first).trim()
        return name to quantityText
    }

    private fun shouldExclude(text: String): Boolean {
        val compact = text.replace(Regex("""\s+"""), "")
        if (compact.any { it in listOf('【', '】', '[', ']') }) return true
        if (compact in setOf("薬品名", "数量", "単位", "品名", "規格")) return true
        if (shelfCodePattern.matches(compact)) return true
        if (pureNumberPattern.matches(compact)) return true
        if (packagingUnitOnlyPattern.matches(compact)) return true
        if (quantityOnlyPattern.matches(compact)) return true
        if (dateTimePattern.containsMatchIn(compact)) return true
        if (compact.length <= 5 &&
            !dosageFormPattern.containsMatchIn(compact) &&
            !strengthPattern.containsMatchIn(compact)
        ) {
            return true
        }
        return false
    }

    private fun isDrugNameCandidate(text: String): Boolean {
        val hasDosageWord = dosageKeywords.any { text.contains(it) }
        val hasStrength = strengthPattern.containsMatchIn(text)
        if (!hasDosageWord && !hasStrength) return false

        val drugNamePart = text
            .replace(dosageFormPattern, "")
            .replace(strengthPattern, "")
            .replace(Regex("""[\s（）()「」『』【】\[\]・･,，.．]+"""), "")
            .trim()
        return drugNamePart.length >= 2
    }

    private fun toHalfWidthDigits(input: String): String {
        return input.map { ch ->
            when (ch) {
                in '０'..'９' -> ('0' + (ch - '０'))
                else -> ch
            }
        }.joinToString("")
    }
}
