package com.example.yakuzaiapp.data.medis

import com.example.yakuzaiapp.data.local.entity.DrugMaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class MedisCsvParserTest {
    private val parser = MedisCsvParser()

    @Test
    fun parsesNew24ColumnHotMasterAndKeepsPriceCodeForSalesPackageLookup() {
        val columns = listOf(
            "1234567890123", // HOT13
            "9876543210123", // HOT7
            "1",
            "2",
            "3",
            "",
            "3399007H1013", // 薬価基準収載医薬品コード
            "3399007H1021", // 個別医薬品コード(YJコード)
            "610463001",
            "610463002",
            "基準名称",
            "バイアスピリン錠１００ｍｇ",
            "バイアスピリン錠１００ｍｇ ＰＴＰ 10錠",
            "100mg",
            "PTP",
            "10",
            "錠",
            "500",
            "錠",
            "内用",
            "バイエル",
            "販売業者",
            "",
            "20260630"
        )

        val csvLine = columns.joinToString(",") { "\"$it\"" }
        val input = ByteArrayInputStream(csvLine.toByteArray(Charset.forName("Shift_JIS")))

        val result = parser.parse(input)
        val record = result.records.single()

        assertEquals(1, result.successCount)
        assertEquals(0, result.skipCount)
        assertNotNull(record)
        assertEquals("3399007H1021", record.yjCode)
        assertEquals("3399007H1013", record.drugCode)
        assertEquals("バイアスピリン錠１００ｍｇ", record.drugName)
        assertEquals("バイアスピリン錠１００ｍｇ ＰＴＰ 10錠", record.packageName)
        assertEquals("1234567890123", record.hot13)
        assertEquals("1234567890123", record.gtin)
        assertEquals("9876543210123", record.gtinSales)
    }
}
