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

private data class DrugLineWithBounds(
    val line: DetectedDrugLine,
    val bounds: OcrBounds?
)

private data class QuantityColumnValue(
    val text: String,
    val bounds: OcrBounds?
)

private data class QuantityFragment(
    val text: String,
    val bounds: OcrBounds?
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
    private val quantityUnitOnlyPattern = Regex(
        """^(?:$quantityUnitPattern)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: Text, includeQuantity: Boolean = true): List<DetectedDrugLine> {
        val lines = text.textBlocks.flatMap { block -> block.lines }.map { line ->
            OcrBlockForParsing(
                text = line.text,
                bounds = line.boundingBox?.toOcrBounds()
            )
        }
        return parseLines(lines, includeQuantity)
    }

    internal fun parseBlocks(
        blocks: List<OcrBlockForParsing>,
        includeQuantity: Boolean = true
    ): List<DetectedDrugLine> {
        return parseLines(
            blocks.flatMap { block ->
                block.text
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { lineText -> block.copy(text = lineText) }
            },
            includeQuantity
        )
    }

    private fun parseLines(
        lines: List<OcrBlockForParsing>,
        includeQuantity: Boolean
    ): List<DetectedDrugLine> {
        val detectedDrugs = mutableListOf<DrugLineWithBounds>()
        val quantityColumns = mutableListOf<QuantityColumnValue>()
        val numericFragments = mutableListOf<QuantityFragment>()
        val unitFragments = mutableListOf<QuantityFragment>()

        for (line in lines) {
            val text = line.text.trim()
            if (text.isBlank()) continue

            val compactText = text.replace(Regex("""\s+"""), "")
            if (pureNumberPattern.matches(compactText)) {
                numericFragments += QuantityFragment(
                    text = toHalfWidthDigits(compactText),
                    bounds = line.bounds
                )
                continue
            }
            if (quantityUnitOnlyPattern.matches(compactText)) {
                unitFragments += QuantityFragment(text = compactText, bounds = line.bounds)
                continue
            }

            val quantityOnlyText = extractQuantityOnlyText(text)
            if (includeQuantity && quantityOnlyText != null) {
                quantityColumns += QuantityColumnValue(quantityOnlyText, line.bounds)
                continue
            }

            val parsed = parseLine(text, line.bounds, includeQuantity)
            if (parsed != null) {
                detectedDrugs += DrugLineWithBounds(parsed, line.bounds)
            }
        }

        quantityColumns += combineQuantityFragments(numericFragments, unitFragments)

        val attachedDrugs = attachQuantities(detectedDrugs, quantityColumns)
        val result = orderDetectedDrugs(
            drugs = detectedDrugs,
            attachedDrugs = attachedDrugs,
            quantities = quantityColumns
        ).distinctBy { "${it.name}|${it.quantityText.orEmpty()}" }

        Log.d(TAG, "detected drug names=${result.size}")
        return result
    }

    private fun parseLine(
        rawText: String,
        bounds: OcrBounds?,
        includeQuantity: Boolean
    ): DetectedDrugLine? {
        val text = rawText.trim()
        if (text.isBlank()) return null
        if (shouldExclude(text, bounds)) return null
        val (normalizedName, extractedQuantityText) = extractDrugNameAndQuantity(text)
        val quantityText = extractedQuantityText.takeIf { includeQuantity }
        if (!isDrugNameCandidate(normalizedName)) return null

        return DetectedDrugLine(
            name = normalizedName,
            quantityText = quantityText,
            sourceLines = listOf(text)
        )
    }

    private fun extractDrugNameAndQuantity(text: String): Pair<String, String?> {
        val match = quantityPattern.find(text)
        if (match == null) return text to null

        val number = toHalfWidthDigits(match.groupValues[1].trim())
        val unit = match.groupValues[2]
        val quantityText = "$number$unit"
        val name = text.substring(0, match.range.first).trim()
        return name to quantityText
    }

    private fun extractQuantityOnlyText(text: String): String? {
        val compact = text.replace(Regex("""\s+"""), "")
        if (!quantityOnlyPattern.matches(compact)) return null
        val match = quantityPattern.matchEntire(compact) ?: return null
        return "${toHalfWidthDigits(match.groupValues[1])}${match.groupValues[2]}"
    }

    private fun combineQuantityFragments(
        numericFragments: List<QuantityFragment>,
        unitFragments: List<QuantityFragment>
    ): List<QuantityColumnValue> {
        val unusedUnitIndexes = unitFragments.indices.toMutableSet()
        return numericFragments.mapNotNull { number ->
            val unitIndex = unusedUnitIndexes
                .mapNotNull { index ->
                    val unit = unitFragments[index]
                    if (isQuantityFragmentPair(number.bounds, unit.bounds)) {
                        index to fragmentDistance(number.bounds, unit.bounds)
                    } else {
                        null
                    }
                }
                .minWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
                ?.first
                ?: return@mapNotNull null
            unusedUnitIndexes -= unitIndex
            val unit = unitFragments[unitIndex]
            QuantityColumnValue(
                text = number.text + unit.text,
                bounds = number.bounds?.union(unit.bounds)
            )
        }
    }

    private fun attachQuantities(
        drugs: List<DrugLineWithBounds>,
        quantities: List<QuantityColumnValue>
    ): List<DetectedDrugLine> {
        val candidatePairs = drugs.indices.flatMap { drugIndex ->
            val drug = drugs[drugIndex]
            if (drug.line.quantityText != null) return@flatMap emptyList()

            quantities.indices.mapNotNull { quantityIndex ->
                val quantity = quantities[quantityIndex]
                val rowDistance = if (isQuantityForDrugRow(drug.bounds, quantity.bounds)) {
                    verticalDistance(drug.bounds, quantity.bounds)
                } else {
                    null
                }
                val columnDistance = if (isQuantityForDrugColumn(drug.bounds, quantity.bounds)) {
                    horizontalDistance(drug.bounds, quantity.bounds)
                } else {
                    null
                }
                val distance = listOfNotNull(rowDistance, columnDistance).minOrNull() ?: return@mapNotNull null
                QuantityCandidatePair(drugIndex, quantityIndex, distance)
            }
        }
        val bestQuantityForDrug = candidatePairs
            .groupBy { it.drugIndex }
            .mapNotNull { (drugIndex, pairs) ->
                pairs.uniqueNearest()?.let { drugIndex to it.quantityIndex }
            }
            .toMap()
        val bestDrugForQuantity = candidatePairs
            .groupBy { it.quantityIndex }
            .mapNotNull { (quantityIndex, pairs) ->
                pairs.uniqueNearest()?.let { quantityIndex to it.drugIndex }
            }
            .toMap()
        val matchedQuantityByDrug = bestQuantityForDrug
            .filter { (drugIndex, quantityIndex) -> bestDrugForQuantity[quantityIndex] == drugIndex }

        return drugs.mapIndexed { drugIndex, detectedDrug ->
            val drug = detectedDrug.line
            if (drug.quantityText != null) return@mapIndexed drug
            val quantityIndex = matchedQuantityByDrug[drugIndex] ?: return@mapIndexed drug
            val quantity = quantities[quantityIndex]
            drug.copy(
                quantityText = quantity.text,
                sourceLines = drug.sourceLines + quantity.text
            )
        }
    }

    private fun orderDetectedDrugs(
        drugs: List<DrugLineWithBounds>,
        attachedDrugs: List<DetectedDrugLine>,
        quantities: List<QuantityColumnValue>
    ): List<DetectedDrugLine> {
        val columnOffsets = drugs.flatMap { drug ->
            quantities.mapNotNull { quantity ->
                if (isQuantityForDrugColumn(drug.bounds, quantity.bounds) &&
                    !isQuantityForDrugRow(drug.bounds, quantity.bounds)
                ) {
                    quantity.bounds!!.centerY() - drug.bounds!!.centerY()
                } else {
                    null
                }
            }
        }
        val indexed = attachedDrugs.indices.toList()
        val sortedIndexes = if (columnOffsets.isNotEmpty()) {
            val quantityIsBelowDrug = columnOffsets.average() > 0
            indexed.sortedWith { first, second ->
                val firstX = drugs[first].bounds?.centerX() ?: Int.MAX_VALUE
                val secondX = drugs[second].bounds?.centerX() ?: Int.MAX_VALUE
                if (quantityIsBelowDrug) secondX.compareTo(firstX) else firstX.compareTo(secondX)
            }
        } else {
            indexed.sortedWith { first, second ->
                val firstBounds = drugs[first].bounds
                val secondBounds = drugs[second].bounds
                val vertical = (firstBounds?.centerY() ?: Int.MAX_VALUE)
                    .compareTo(secondBounds?.centerY() ?: Int.MAX_VALUE)
                if (vertical != 0) vertical else {
                    (firstBounds?.centerX() ?: Int.MAX_VALUE)
                        .compareTo(secondBounds?.centerX() ?: Int.MAX_VALUE)
                }
            }
        }
        return sortedIndexes.map { attachedDrugs[it] }
    }

    private data class QuantityCandidatePair(
        val drugIndex: Int,
        val quantityIndex: Int,
        val distance: Int
    )

    private fun List<QuantityCandidatePair>.uniqueNearest(): QuantityCandidatePair? {
        val minimumDistance = minOfOrNull { it.distance } ?: return null
        val nearest = filter { it.distance == minimumDistance }
        return nearest.singleOrNull()
    }

    private fun isQuantityForDrugRow(drugBounds: OcrBounds?, quantityBounds: OcrBounds?): Boolean {
        drugBounds ?: return false
        quantityBounds ?: return false
        if (quantityBounds.left < drugBounds.right) return false

        val maximumVerticalDistance = maxOf(drugBounds.height(), quantityBounds.height(), 24)
        return verticalDistance(drugBounds, quantityBounds) <= maximumVerticalDistance
    }

    private fun isQuantityForDrugColumn(drugBounds: OcrBounds?, quantityBounds: OcrBounds?): Boolean {
        drugBounds ?: return false
        quantityBounds ?: return false
        val isAboveOrBelow = quantityBounds.bottom <= drugBounds.top || quantityBounds.top >= drugBounds.bottom
        if (!isAboveOrBelow) return false

        val maximumHorizontalDistance = maxOf(drugBounds.width(), quantityBounds.width(), 24)
        return horizontalDistance(drugBounds, quantityBounds) <= maximumHorizontalDistance
    }

    private fun verticalDistance(first: OcrBounds?, second: OcrBounds?): Int {
        if (first == null || second == null) return Int.MAX_VALUE
        return kotlin.math.abs(first.centerY() - second.centerY())
    }

    private fun horizontalDistance(first: OcrBounds?, second: OcrBounds?): Int {
        if (first == null || second == null) return Int.MAX_VALUE
        return kotlin.math.abs(first.centerX() - second.centerX())
    }

    private fun OcrBounds.centerY(): Int = (top + bottom) / 2

    private fun OcrBounds.centerX(): Int = (left + right) / 2

    private fun OcrBounds.height(): Int = bottom - top

    private fun OcrBounds.width(): Int = right - left

    private fun OcrBounds.union(other: OcrBounds?): OcrBounds {
        other ?: return this
        return OcrBounds(
            left = minOf(left, other.left),
            top = minOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom)
        )
    }

    private fun isQuantityFragmentPair(numberBounds: OcrBounds?, unitBounds: OcrBounds?): Boolean {
        numberBounds ?: return false
        unitBounds ?: return false
        val maximumGap = maxOf(
            numberBounds.width(),
            numberBounds.height(),
            unitBounds.width(),
            unitBounds.height(),
            24
        ) * 2
        val sameRow = verticalDistance(numberBounds, unitBounds) <= maximumGap &&
            horizontalGap(numberBounds, unitBounds) <= maximumGap
        val sameColumn = horizontalDistance(numberBounds, unitBounds) <= maximumGap &&
            verticalGap(numberBounds, unitBounds) <= maximumGap
        return sameRow || sameColumn
    }

    private fun fragmentDistance(first: OcrBounds?, second: OcrBounds?): Int {
        return horizontalGap(first, second) + verticalGap(first, second)
    }

    private fun horizontalGap(first: OcrBounds?, second: OcrBounds?): Int {
        if (first == null || second == null) return Int.MAX_VALUE
        return maxOf(0, maxOf(first.left, second.left) - minOf(first.right, second.right))
    }

    private fun verticalGap(first: OcrBounds?, second: OcrBounds?): Int {
        if (first == null || second == null) return Int.MAX_VALUE
        return maxOf(0, maxOf(first.top, second.top) - minOf(first.bottom, second.bottom))
    }

    private fun shouldExclude(text: String, bounds: OcrBounds?): Boolean {
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
