package com.example.yakuzaiapp.domain.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentOcrParserTest {
    @Test
    fun parseBlocks_extractsDrugNamesOnly() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("薬品名"),
                block("数量"),
                block("単位"),
                block("デエビゴ錠5mg"),
                block("14錠"),
                block("まるえい調剤薬局")
            )
        )

        assertEquals(listOf("デエビゴ錠5mg"), result.map { it.name })
    }

    @Test
    fun parseBlocks_matchesDosageKeywords() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("ヒルドイドソフト軟膏0.3%"),
                block("ビソノテープ4mg"),
                block("ツムラ抑肝散エキス顆粒2.5g")
            )
        )

        assertEquals(
            listOf(
                "ヒルドイドソフト軟膏0.3%",
                "ビソノテープ4mg",
                "ツムラ抑肝散エキス顆粒2.5g"
            ),
            result.map { it.name }
        )
    }

    @Test
    fun parseBlocks_splitsMultipleLinesInsideOneTextBlock() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("センノシド錠12mg\nデエビゴ錠5mg")
            )
        )

        assertEquals(
            listOf("センノシド錠12mg", "デエビゴ錠5mg"),
            result.map { it.name }
        )
    }

    @Test
    fun parseBlocks_ignoresQuantityColumnLinesInsideOneTextBlock() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("5錠\n1錠\n1錠\n2錠")
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseBlocks_detectsSingleDrugNameBlock() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("ロキソプロフェン錠60mg")
            )
        )

        assertEquals(listOf("ロキソプロフェン錠60mg"), result.map { it.name })
    }

    @Test
    fun parseBlocks_ignoresShelfDateHeaderAndNumberOnlyBlocks() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("薬品名"),
                block("規格"),
                block("【注意事項"),
                block("[備考]"),
                block("A12"),
                block("2026年6月15日"),
                block("6月15日"),
                block("10:30"),
                block("123"),
                block("14錠"),
                block("知多厚生総合病院センター")
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseBlocks_deduplicatesSameDrugName() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("ラメルテオン錠8mg（ロゼレム）"),
                block("ラメルテオン錠8mg（ロゼレム）")
            )
        )

        assertEquals(1, result.size)
        assertEquals("ラメルテオン錠8mg（ロゼレム）", result.single().name)
    }

    @Test
    fun parseBlocks_ignoresDosageFormOnlyBlocks() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("錠"),
                block("1錠"),
                block("2錠"),
                block("カプセル")
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseBlocks_ignoresAdditionalPackagingUnitOnlyBlocks() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("アンプル"),
                block("バイアル"),
                block("キット"),
                block("セット"),
                block("束"),
                block("巻")
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseBlocks_ignoresQuantityWithAdditionalPackagingUnits() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("1アンプル"),
                block("2バイアル"),
                block("1キット"),
                block("3セット"),
                block("2束"),
                block("1巻"),
                block("1 アンプル"),
                block("2 バイアル")
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseBlocks_keepsDrugNamesWithDosageFormOrStrength() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("ロキソプロフェン錠60mg"),
                block("新レシカルボン坐剤"),
                block("新レシカルボン学剤")
            )
        )

        assertEquals(
            listOf("ロキソプロフェン錠60mg", "新レシカルボン坐剤", "新レシカルボン学剤"),
            result.map { it.name }
        )
    }

    @Test
    fun parseBlocks_assignsRightColumnQuantitiesToTheirMatchingRows() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("デエビゴ錠5mg", left = 40, top = 100, right = 360, bottom = 140),
                block("2錠", left = 820, top = 102, right = 880, bottom = 140),
                block("ロキソプロフェン錠60mg", left = 40, top = 180, right = 420, bottom = 220),
                block("1錠", left = 820, top = 182, right = 880, bottom = 220)
            )
        )

        assertEquals(listOf("2錠", "1錠"), result.map { it.quantityText })
        assertEquals(
            listOf(
                listOf("デエビゴ錠5mg", "2錠"),
                listOf("ロキソプロフェン錠60mg", "1錠")
            ),
            result.map { it.sourceLines }
        )
    }

    @Test
    fun parseBlocks_doesNotAssignQuantityFromAnotherTableRow() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("デエビゴ錠5mg", left = 40, top = 100, right = 360, bottom = 140),
                block("2錠", left = 820, top = 260, right = 880, bottom = 300)
            )
        )

        assertEquals(1, result.size)
        assertEquals(null, result.single().quantityText)
    }

    @Test
    fun parseBlocks_omitsQuantitiesWhenDisabled() {
        val result = DocumentOcrParser.parseBlocks(
            listOf(
                block("デエビゴ錠5mg", left = 40, top = 100, right = 360, bottom = 140),
                block("2錠", left = 820, top = 102, right = 880, bottom = 140)
            ),
            includeQuantity = false
        )

        assertEquals(listOf("デエビゴ錠5mg"), result.map { it.name })
        assertEquals(listOf(null), result.map { it.quantityText })
    }

    private fun block(
        text: String,
        left: Int? = null,
        top: Int? = null,
        right: Int? = null,
        bottom: Int? = null
    ): OcrBlockForParsing {
        val bounds = if (left != null && top != null && right != null && bottom != null) {
            OcrBounds(left, top, right, bottom)
        } else {
            null
        }
        return OcrBlockForParsing(text = text, bounds = bounds)
    }
}
