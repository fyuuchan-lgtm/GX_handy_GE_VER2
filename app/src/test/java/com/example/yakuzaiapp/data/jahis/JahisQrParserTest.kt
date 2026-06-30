package com.example.yakuzaiapp.data.jahis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JahisQrParserTest {

    @Test
    fun detectsVersionJAHISTC01() {
        val result = JahisQrParser.parse(
            """
            JAHISTC01
            1,テスト　太郎,1,19530514
            """.trimIndent()
        )

        assertEquals("JAHISTC01", result.version)
    }

    @Test
    fun detectsVersionJAHISTC02() {
        val result = JahisQrParser.parse(
            """
            JAHISTC02
            1,山田 花子,2,S400505
            """.trimIndent()
        )

        assertEquals("JAHISTC02", result.version)
    }

    @Test
    fun parsesJAHISTC01SimpleDrugRecord() {
        val text = """
            JAHISTC01
            1,テスト　太郎,1,19530514
            5,20260608
            201,1,★マグミット錠330mg　【簡】,3,錠,4,2344009F2031
            301,1,1日3回 朝・昼・夕食後,7,日分,1,1,
        """.trimIndent()

        val result = JahisQrParser.parse(text)
        val drug = result.rps.single().drugs.single()

        assertEquals("JAHISTC01", result.version)
        assertEquals("テスト　太郎", result.patientName)
        assertEquals("20260608", result.dispensingDate)
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("2344009F2031", drug.drugCode)
        assertEquals("★マグミット錠330mg　【簡】", drug.drugName)
        assertEquals("3", drug.quantity)
        assertEquals("錠", drug.unit)
        assertEquals("1日3回 朝・昼・夕食後", result.rps.single().usage)
    }

    @Test
    fun parsesJAHISTC01YuyamaFullRecord() {
        val text = """
            JAHISTC01
            1,テスト　太郎,1,19530514
            5,20260608
            11,○○病院,23,1,2402213
            51,○○病院 ,23,1,2402213
            55,センター医師,整形外科
            201,1,★マグミット錠330mg　【簡】,3,錠,4,2344009F2031
            301,1,1日3回 朝・昼・夕食後,7,日分,1,1,
        """.trimIndent()

        val result = JahisQrParser.parse(text)
        val drug = result.rps.single().drugs.single()

        assertEquals("テスト　太郎", result.patientName)
        assertEquals("1", result.patientGender)
        assertEquals("19530514", result.patientBirthDate)
        assertEquals("20260608", result.dispensingDate)
        assertEquals("○○病院", result.pharmacyName)
        assertEquals("○○病院", result.prescribingHospital)
        assertEquals("センター医師", result.doctorName)
        assertEquals("整形外科", result.department)
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("2344009F2031", drug.drugCode)
        assertEquals("★マグミット錠330mg　【簡】", drug.drugName)
    }

    @Test
    fun parsesJAHISTC02DrugRecordWithoutRegression() {
        val text = """
            JAHISTC02
            1,山田 花子,2,S400505
            5,20260606
            201,1,1,1,4,2171019F1024,アムロジピン錠5mg,2,1,錠
            301,1,1日1回朝食後 服用
        """.trimIndent()

        val result = JahisQrParser.parse(text)
        val drug = result.rps.single().drugs.single()

        assertEquals("JAHISTC02", result.version)
        assertEquals(DrugCodeType.YJ, drug.drugCodeType)
        assertEquals("2171019F1024", drug.drugCode)
        assertEquals("アムロジピン錠5mg", drug.drugName)
        assertEquals("2", drug.quantity)
        assertEquals("錠", drug.unit)
        assertEquals("1日1回朝食後 服用", result.rps.single().usage)
    }

    @Test
    fun mapsDrugCodeTypesCorrectly() {
        assertEquals(DrugCodeType.RECEIPT_COMPUTER, DrugCodeType.fromCode("2"))
        assertEquals(DrugCodeType.YJ, DrugCodeType.fromCode("4"))
        assertEquals(DrugCodeType.HOT, DrugCodeType.fromCode("6"))
        assertEquals(DrugCodeType.GENERIC_MHLW, DrugCodeType.fromCode("7"))
        assertEquals(DrugCodeType.UNKNOWN, DrugCodeType.fromCode("0"))
    }

    @Test
    fun skipsInvalidRecordsWithoutCrashing() {
        val text = """
            JAHISTC02
            invalid-line
            201,not-a-number,1,1,4,2171019F1024,アムロジピン錠5mg,2,1,錠
            301,1
            999,foo,bar
            1,山田 花子,2,S400505
            5,20260606
        """.trimIndent()

        val result = JahisQrParser.parse(text)

        assertEquals("JAHISTC02", result.version)
        assertFalse(result.rps.isNotEmpty() && result.rps.any { it.drugs.isNotEmpty() })
        assertNotNull(result.patientName)
    }

    @Test
    fun handlesVersionOnlyInputWithoutCrashing() {
        val result = JahisQrParser.parse("JAHISTC02")

        assertEquals("JAHISTC02", result.version)
        assertTrue(result.rps.isEmpty())
    }

    @Test
    fun detectsVersionJAHISTC07() {
        val result = JahisQrParser.parse(
            """
            JAHISTC07
            1,Test User,1,20000101
            """.trimIndent()
        )

        assertEquals("JAHISTC07", result.version)
    }

    @Test
    fun parsesJAHISTC07HeaderWithSuffixWithoutInvalidRecord() {
        val result = JahisQrParser.parse(
            """
            JAHISTC07,1
            1,Test User,1,20000101
            201,1,Drug A,1,錠,4,1234567A1010
            301,1,1日1回 朝食後,7,日分,1,1,
            """.trimIndent()
        )

        assertEquals("JAHISTC07", result.version)
        assertEquals(1, result.rps.sumOf { it.drugs.size })
    }
}
