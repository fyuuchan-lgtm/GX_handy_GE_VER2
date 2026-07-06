package com.example.yakuzaiapp.domain.jahis

import com.example.yakuzaiapp.data.jahis.JahisQrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JahisQrAssemblerTest {
    @Test
    fun twoQrSampleProducesThreeItems() {
        val frags = listOf(
            detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    11,Test Pharmacy,23,1,2402213
                    51,Test Hospital,23,1,2402213
                    55,Test Doctor,Dept
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Drug B,1,錠,4,2222222222222
                    301,2,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    220
                ),
                detected(
                    """
                    201,3,Drug C,1,錠,4,3333333333333
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                )
            )
        val result = JahisQrAssembler.tryAssemble(frags)

        assertTrue(result is AssembleResult.Success)
        val parsed = JahisQrParser.parse((result as AssembleResult.Success).fullText)
        assertEquals(3, parsed.rps.flatMap { it.drugs }.size)
    }


    @Test
    fun threeQrSampleProducesSixItems() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    11,Test Pharmacy,23,1,2402213
                    51,Test Hospital,23,1,2402213
                    55,Test Doctor,Dept
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Drug B,1,錠,4,2222222222222
                    301,2,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    260
                ),
                detected(
                    """
                    201,3,Drug C,1,錠,4,3333333333333
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    201,4,Drug D,1,錠,4,4444444444444
                    301,4,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                ),
                detected(
                    """
                    201,5,Drug E,1,錠,4,5555555555555
                    301,5,1日1回朝食後,7,日分,1,1,
                    201,6,Drug F,1,錠,4,6666666666666
                    301,6,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    520
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val parsed = JahisQrParser.parse((result as AssembleResult.Success).fullText)
        assertEquals(6, parsed.rps.flatMap { it.drugs }.size)
    }

    @Test
    fun threeQrSampleProducesSixItemsWhenScanPositionOrderIsMisleading() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    201,5,Drug E,1,錠,4,5555555555555
                    301,5,1日1回朝食後,7,日分,1,1,
                    201,6,Drug F,1,錠,4,6666666666666
                    301,6,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                ),
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    11,Test Pharmacy,23,1,2402213
                    51,Test Hospital,23,1,2402213
                    55,Test Doctor,Dept
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Drug B,1,錠,4,2222222222222
                    301,2,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    500
                ),
                detected(
                    """
                    201,3,Drug C,1,錠,4,3333333333333
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    201,4,Drug D,1,錠,4,4444444444444
                    301,4,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    900
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val parsed = JahisQrParser.parse((result as AssembleResult.Success).fullText)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), parsed.rps.map { it.rpNumber })
    }

    @Test
    fun structuredAppendSequenceOverridesMisleadingRpAndPositionOrder() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    201,5,Drug E,1,錠,4,5555555555555
                    301,5,1日1回朝食後,7,日分,1,1,
                    201,6,Drug F,1,錠,4,6666666666666
                    301,6,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    left = 0,
                    saSequence = 2
                ),
                detected(
                    """
                    201,3,Drug C,1,錠,4,3333333333333
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    201,4,Drug D,1,錠,4,4444444444444
                    301,4,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    left = 900,
                    saSequence = 1
                ),
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Drug B,1,錠,4,2222222222222
                    301,2,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    left = 500,
                    saSequence = 0
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val parsed = JahisQrParser.parse((result as AssembleResult.Success).fullText)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), parsed.rps.map { it.rpNumber })
    }

    @Test
    fun headersComeFirstThenOthersByLeft() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    201,2,Drug B,1,錠,4,2222222222222
                    301,2,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                ),
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    200
                ),
            )
        )

        assertTrue(result is AssembleResult.Success)
        val fullText = (result as AssembleResult.Success).fullText
        assertTrue(fullText.startsWith("JAHISTC01"))
        val parsed = JahisQrParser.parse(fullText)
        assertEquals(listOf(1, 2), parsed.rps.map { it.rpNumber })
    }

    @Test
    fun emptyInputReturnsNoHeader() {
        val result = JahisQrAssembler.tryAssemble(emptyList())

        assertTrue(result is AssembleResult.NoHeader)
    }

    @Test
    fun missingHeaderReturnsIncomplete() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected("201,1,繝繝溘・,1,骭,4,1234567890123", 0),
                detected("301,1,逕ｨ豕・", 20),
            )
        )

        assertTrue(result is AssembleResult.Incomplete)
        assertEquals(IncompleteReason.MISSING_HEADER, (result as AssembleResult.Incomplete).reason)
    }

    @Test
    fun onlyFirstFragmentReturnsIncomplete() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    201,1,Drug A,1,錠,4,1111111111111
                    """.trimIndent(),
                    0
                )
            )
        )

        assertTrue(result is AssembleResult.Incomplete)
        assertEquals(IncompleteReason.MISSING_USAGE, (result as AssembleResult.Incomplete).reason)
    }

    @Test
    fun partialUsageRecordsReturnsIncomplete() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Drug B,1,錠,4,2222222222222
                    """.trimIndent() + "\n",
                    0
                )
            )
        )

        assertTrue(result is AssembleResult.Incomplete)
        assertEquals(IncompleteReason.MISSING_USAGE, (result as AssembleResult.Incomplete).reason)
    }

    @Test
    fun missingRpNumberReturnsIncomplete() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Drug B,1,錠,4,2222222222222
                    301,2,1日2回朝夕,7,日分,1,1,
                    201,4,Drug D,1,錠,4,4444444444444
                    301,4,1日1回夕食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                )
            )
        )

        assertTrue(result is AssembleResult.Incomplete)
        assertEquals(IncompleteReason.MISSING_RP, (result as AssembleResult.Incomplete).reason)
        assertTrue(result.detailMessage?.contains("RP3") == true)
    }

    @Test
    fun orphanUsageRecordReturnsIncomplete() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    301,1,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                )
            )
        )

        assertTrue(result is AssembleResult.Incomplete)
        assertEquals(IncompleteReason.MISSING_DRUG_RECORD, (result as AssembleResult.Incomplete).reason)
        assertTrue(result.detailMessage?.contains("RP1") == true)
    }

    @Test
    fun malformedDrugRecordReturnsIncompleteEvenWhenUsageExists() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Broken Drug
                    301,2,1日1回就寝前,7,日分,1,1,
                    201,3,Drug C,1,錠,4,3333333333333
                    301,3,1日3回朝昼夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                )
            )
        )

        assertTrue(result is AssembleResult.Incomplete)
        assertEquals(IncompleteReason.MISSING_DRUG_RECORD, (result as AssembleResult.Incomplete).reason)
        assertTrue(result.detailMessage?.contains("RP2") == true)
    }

    @Test
    fun invalidQrSetReturnsParseFailed() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected("JAHISTC01\n201,1,繝繝溘・", 0)
            )
        )

        assertTrue(result is AssembleResult.ParseFailed || result is AssembleResult.Incomplete)
        if (result is AssembleResult.Incomplete) {
            assertTrue(
                result.reason == IncompleteReason.MISSING_HEADER ||
                    result.reason == IncompleteReason.UNTERMINATED ||
                    result.reason == IncompleteReason.MISSING_USAGE ||
                    result.reason == IncompleteReason.MISSING_DRUG_RECORD
            )
        }
    }

    @Test
    fun jahisTc07HeaderIsAcceptedAsJahis() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC07
                    1,Test User,1,20000101
                    5,20260608
                    """.trimIndent(),
                    0
                )
            )
        )

        assertTrue(result !is AssembleResult.NoHeader)
    }

    @Test
    fun fragmentBoundaryAtRecordBoundaryPreservesAllDrugs() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,19491027
                    5,20260608
                    11,Test Pharmacy,23,1,2402213
                    51,Test Hospital,23,1,2402213
                    55,Test Doctor,Dept
                    201,1,Drug A,1,錠,4,1190027F2029
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,Drug B,2,錠,4,3339003F5020
                    301,2,1日2回朝夕食後,7,日分,1,1,
                    201,3,Drug C,3,包,4,5200139D1037
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    201,4,Drug D,4,錠,4,2344009F2031
                    301,4,1日2回朝夕食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    120
                ),
                detected(
                    """
                    201,5,Drug E,1,錠,4,2329023F1071
                    301,5,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    240
                ),
                detected(
                    """
                    201,6,Drug F,3,枚,4,2149700S1027
                    301,6,貼付1日1回1回1枚,1,調剤,5,1,
                    """.trimIndent() + "\n",
                    360
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val parsed = JahisQrParser.parse((result as AssembleResult.Success).fullText)
        assertEquals(6, parsed.rps.flatMap { it.drugs }.size)
        assertTrue(parsed.rps.any { rp ->
            rp.rpNumber == 5 && rp.drugs.any { it.drugCode == "2329023F1071" }
        })
    }

    @Test
    fun drugRecordSplitAcrossTwoFragmentsIsReassembledCorrectly() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,谷川長子,1,19561116
                    5,20260609
                    11,知多厚生総合病院センター,23,1,2402213
                    51,知多厚生総合病院センター,23,1,2402213
                    55,テスト医師,内科
                    201,1,☆デエビゴ錠5mg　粒・粉末・,1,錠,4,1190027F2029
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,☆ラメルテオン錠8m
                    """.trimIndent(),
                    120
                ),
                detected(
                    """
                    g（ロゼレム）　粒・粉末・,1,錠,4,1190016F1075
                    301,2,1日1回就寝前,7,日分,1,1,
                    201,3,☆マグミット錠330mg　粒,3,錠,4,2344009F2031
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    240
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val parsed = JahisQrParser.parse((result as AssembleResult.Success).fullText)
        val drugs = parsed.rps.flatMap { it.drugs }
        assertEquals(3, drugs.size)
        assertTrue(drugs.any { it.drugCode == "1190016F1075" })
    }

    @Test
    fun drugRecordSplitAcrossTwoFragmentsIgnoresMisleadingPositionOrder() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    g（ロゼレム）　粒・粉末・,1,錠,4,1190016F1075
                    301,2,1日1回就寝前,7,日分,1,1,
                    201,3,☆マグミット錠330mg　粒,3,錠,4,2344009F2031
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    0
                ),
                detected(
                    """
                    JAHISTC01
                    1,谷川長子,1,19561116
                    5,20260609
                    11,知多厚生総合病院センター,23,1,2402213
                    51,知多厚生総合病院センター,23,1,2402213
                    55,テスト医師,内科
                    201,1,☆デエビゴ錠5mg　粒・粉末・,1,錠,4,1190027F2029
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,☆ラメルテオン錠8m
                    """.trimIndent(),
                    900
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val fullText = (result as AssembleResult.Success).fullText
        assertTrue(fullText.contains("201,2,☆ラメルテオン錠8mg（ロゼレム）"))
        val parsed = JahisQrParser.parse(fullText)
        assertTrue(parsed.rps.any { rp -> rp.drugs.any { it.drugCode == "1190016F1075" } })
    }

    @Test
    fun rp2ContinuationWithoutNewlineIsJoinedWithoutDelimiter() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,谷川長子,1,19561116
                    5,20260609
                    11,知多厚生総合病院センター,23,1,2402213
                    201,1,☆デエビゴ錠5mg　粒・粉末・,1,錠,4,1190027F2029
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,☆ラメルテオン錠8m
                    """.trimIndent(),
                    100
                ),
                detected(
                    """
                    g（ロゼレム）　粒・粉末・,1,錠,4,1190016F1075
                    301,2,1日1回就寝前,7,日分,1,1,
                    """.trimIndent() + "\n",
                    200
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val fullText = (result as AssembleResult.Success).fullText
        assertTrue(fullText.contains("201,2,☆ラメルテオン錠8mg（ロゼレム）"))
        val parsed = JahisQrParser.parse(fullText)
        assertTrue(parsed.rps.any { rp -> rp.drugs.any { it.drugCode == "1190016F1075" } })
    }

    @Test
    fun rp5NewRecordGetsNewlineInserted() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,谷川長子,1,19561116
                    5,20260609
                    11,知多厚生総合病院センター,23,1,2402213
                    51,知多厚生総合病院センター,23,1,2402213
                    201,1,☆デエビゴ錠5mg　粒・粉末・,1,錠,4,1190027F2029
                    301,1,1日1回朝食後,7,日分,1,1,
                    201,2,☆ラメルテオン錠8mg（ロゼレム）,1,錠,4,1190016F1075
                    301,2,1日1回就寝前,7,日分,1,1,
                    201,3,☆マグミット錠330mg　粒,3,錠,4,2344009F2031
                    301,3,1日3回朝昼夕食後,7,日分,1,1,
                    201,4,☆ランソプラゾールOD錠15mg（タケプロン）【簡】,1,錠,4,2329023F1071
                    301,4,1日1回朝食後,7,日分,1,1,
                    """.trimIndent(),
                    100
                ),
                detected(
                    """
                    201,5,☆ランソプラゾールOD錠15mg（タケプロン）【簡】,1,錠,4,2329023F1071
                    301,5,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    200
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val fullText = (result as AssembleResult.Success).fullText
        assertTrue(fullText.contains("301,4,1日1回朝食後,7,日分,1,1,\n201,5,"))
        val parsed = JahisQrParser.parse(fullText)
        assertTrue(parsed.rps.any { rp -> rp.drugs.any { it.drugCode == "2329023F1071" } })
    }

    @Test
    fun alreadyHasNewlineDoesNotInsertExtraDelimiter() {
        val result = JahisQrAssembler.tryAssemble(
            listOf(
                detected(
                    """
                    JAHISTC01
                    1,Test User,1,20000101
                    5,20260608
                    201,1,Drug A,1,錠,4,1111111111111
                    301,1,1日1回朝食後,7,日分,1,1,
                    """.trimIndent() + "\n",
                    10
                ),
                detected(
                    """
                    201,2,Drug B,1,錠,4,2222222222222
                    301,2,1日2回朝夕,7,日分,1,1,
                    """.trimIndent() + "\n",
                    20
                )
            )
        )

        assertTrue(result is AssembleResult.Success)
        val fullText = (result as AssembleResult.Success).fullText
        assertTrue(fullText.contains("301,1,1日1回朝食後,7,日分,1,1,\n201,2,Drug B"))
        assertTrue(!fullText.contains("\n\n201,2,Drug B"))
        val parsed = JahisQrParser.parse(fullText)
        assertEquals(2, parsed.rps.flatMap { it.drugs }.size)
    }

    private fun detected(
        text: String,
        left: Int,
        saSequence: Int? = null
    ): DetectedQr = DetectedQr(text = text, left = left, saSequence = saSequence)
}

