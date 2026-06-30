package com.example.yakuzaiapp.data.jahis

import org.junit.Assert.assertEquals
import org.junit.Test

class JahisQrParserDiagnosticTest {

    @Test
    fun parsesTc01YjCodeAsDrugCode() {
        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            201,1,Aspirin Tablet 100mg,1,TAB,4,3399007H1013
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals("JAHISTC01", result.version)
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("3399007H1013", drug.drugCode)
        assertEquals("Aspirin Tablet 100mg", drug.drugName)
        assertEquals("1", drug.quantity)
        assertEquals("TAB", drug.unit)
    }

    @Test
    fun parsesTc07YjCodeAsDrugCode() {
        val result = JahisQrParser.parse(
            """
            JAHISTC07
            1,Patient,1,20000101
            5,20260609
            201,1,Aspirin Tablet 100mg,1,TAB,4,3399007H1013
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals("JAHISTC07", result.version)
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("3399007H1013", drug.drugCode)
        assertEquals("Aspirin Tablet 100mg", drug.drugName)
    }

    @Test
    fun parsesTc01MhlwCodeAsMhlw() {
        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            201,1,Aspirin Tablet 100mg,1,TAB,3,610463001
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals(DrugCodeType.MHLW, drug.drugCodeType)
        assertEquals("610463001", drug.drugCode)
        assertEquals("Aspirin Tablet 100mg", drug.drugName)
    }

    @Test
    fun parsesTc01ReceiptComputerCodeAsReceiptComputer() {
        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            201,1,Aspirin Tablet 100mg,1,TAB,2,610463001
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals(DrugCodeType.RECEIPT_COMPUTER, drug.drugCodeType)
        assertEquals("610463001", drug.drugCode)
    }

    @Test
    fun parsesConcatenatedFragmentsJoinedBeforeParse() {
        val fragment1 = """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            201,1,Aspirin
        """.trimIndent()
        val fragment2 = """
             Tablet 100mg,1,TAB,4,3399007H1013
            301,1,Take after meal,7,days,1,1,
        """.trimIndent()

        val result = JahisQrParser.parse(fragment1 + fragment2)
        val drug = result.rps.single().drugs.single()

        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("3399007H1013", drug.drugCode)
        assertEquals("Aspirin Tablet 100mg", drug.drugName)
    }

    @Test
    fun case6_fullWidthCommaInDrugNameIsNotSplit() {
        val line = "201,1,バイアスピリン錠，100mg,1,錠,4,3399007H1013"
        val cols = line.split(',')

        assertEquals(
            listOf(
                "201",
                "1",
                "バイアスピリン錠，100mg",
                "1",
                "錠",
                "4",
                "3399007H1013"
            ),
            cols
        )

        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            $line
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("3399007H1013", drug.drugCode)
        assertEquals("バイアスピリン錠，100mg", drug.drugName)
        assertEquals("1", drug.quantity)
        assertEquals("錠", drug.unit)
    }

    @Test
    fun case7_halfWidthCommaInDrugNameShiftsColumns() {
        val line = "201,1,バイアスピリン錠,100mg,1,錠,4,3399007H1013"
        val cols = line.split(',')

        assertEquals(
            listOf(
                "201",
                "1",
                "バイアスピリン錠",
                "100mg",
                "1",
                "錠",
                "4",
                "3399007H1013"
            ),
            cols
        )

        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            $line
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals(DrugCodeType.UNKNOWN, drug.drugCodeType)
        assertEquals("4", drug.drugCode)
        assertEquals("バイアスピリン錠", drug.drugName)
        assertEquals("100mg", drug.quantity)
        assertEquals("1", drug.unit)
    }

    @Test
    fun case8_mojibakeSuffixInDrugNameKeepsYjCode() {
        val line = "201,1,バイアスピリン錠100mg繝ｳ,1,錠,4,3399007H1013"
        val cols = line.split(',')

        assertEquals(
            listOf(
                "201",
                "1",
                "バイアスピリン錠100mg繝ｳ",
                "1",
                "錠",
                "4",
                "3399007H1013"
            ),
            cols
        )

        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            $line
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("3399007H1013", drug.drugCode)
        assertEquals("バイアスピリン錠100mg繝ｳ", drug.drugName)
        assertEquals("1", drug.quantity)
        assertEquals("錠", drug.unit)
    }

    @Test
    fun case9_insufficientColumnsAreSkipped() {
        val line = "201,1,バイアスピリン錠100mg,1,錠"
        val cols = line.split(',')

        assertEquals(listOf("201", "1", "バイアスピリン錠100mg", "1", "錠"), cols)

        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,Patient,1,20000101
            5,20260609
            $line
            """.trimIndent()
        )

        assertEquals("JAHISTC01", result.version)
        assertEquals(0, result.rps.singleOrNull()?.drugs?.size ?: 0)
    }

    @Test
    fun case10_tc02ColumnPositionsMatchExpected() {
        val line = "201,1,1,1,4,3399007H1013,バイアスピリン錠100mg,1,1,錠,,,"
        val cols = line.split(',')

        assertEquals(
            listOf(
                "201",
                "1",
                "1",
                "1",
                "4",
                "3399007H1013",
                "バイアスピリン錠100mg",
                "1",
                "1",
                "錠",
                "",
                "",
                ""
            ),
            cols
        )

        val result = JahisQrParser.parse(
            """
            JAHISTC02
            1,Patient,1,20000101
            5,20260609
            $line
            301,1,Take after meal,7,days,1,1,
            """.trimIndent()
        )

        val drug = result.rps.single().drugs.single()
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("3399007H1013", drug.drugCode)
        assertEquals("バイアスピリン錠100mg", drug.drugName)
        assertEquals("1", drug.quantity)
        assertEquals("錠", drug.unit)
    }
}
