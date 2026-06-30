package com.example.yakuzaiapp.util

import com.example.yakuzaiapp.data.local.entity.DrugMaster

object ColumnMapper {
    val COLUMN_MAPPINGS: Map<String, List<String>> = mapOf(
        "drug_code" to listOf("drug_code", "薬品コード", "院内コード", "薬剤コード", "コード", "code"),
        "drug_name" to listOf("drug_name", "薬品名", "品名", "薬剤名", "name"),
        "alias" to listOf("alias", "別名", "略称", "通称"),
        "package_spec" to listOf("package_spec", "規格", "包装", "包装規格", "spec"),
        "quantity" to listOf("quantity", "入数", "数量", "qty"),
        "unit" to listOf("unit", "単位"),
        "price" to listOf("price", "薬価", "価格"),
        "yj_code" to listOf("yj_code", "YJコード", "YJ", "yjcode"),
        "jan_code" to listOf("jan_code", "JANコード", "JAN", "jancode"),
        "gtin" to listOf("gtin", "GTIN", "GS1コード", "GS1(調剤包装単位)", "調剤包装GTIN", "gs1_code", "GS1調剤包装", "調剤包装"),
        "gtin_sales" to listOf("gtin_sales", "GS1(販売包装単位)", "販売包装GTIN", "販売包装"),
        "gtin_case" to listOf("gtin_case", "GS1(元梱包装単位)", "元梱包装GTIN", "元梱包装"),
        "year" to listOf("year", "年度", "年")
    )

    fun autoMap(csvHeaders: List<String>): Map<String, String?> {
        return COLUMN_MAPPINGS.mapValues { (_, aliases) ->
            csvHeaders.firstOrNull { header ->
                aliases.any { alias -> header.equals(alias, ignoreCase = true) }
            }
        }
    }

    fun validateRequired(mapping: Map<String, String?>): List<String> {
        val required = listOf("drug_code", "drug_name", "gtin")
        return required.filter { mapping[it].isNullOrBlank() }
            .map { "必須列がマッピングされていません: $it" }
    }

    fun toDrugMaster(
        row: Map<String, String>,
        mapping: Map<String, String?>
    ): DrugMaster? {
        val drugCode = row[mapping["drug_code"]].orEmpty().trim()
        val drugName = row[mapping["drug_name"]].orEmpty().trim()
        val gtin = normalizeGtin(row[mapping["gtin"]].orEmpty())

        if (drugCode.isBlank() || drugName.isBlank() || gtin.isBlank()) return null

        return DrugMaster(
            hot13 = gtin,
            drugCode = drugCode,
            drugName = drugName,
            alias = row[mapping["alias"]]?.takeIf { it.isNotBlank() },
            packageSpec = row[mapping["package_spec"]]?.takeIf { it.isNotBlank() },
            quantity = row[mapping["quantity"]]?.toIntOrNull(),
            unit = row[mapping["unit"]]?.takeIf { it.isNotBlank() },
            price = row[mapping["price"]]?.toIntOrNull(),
            yjCode = row[mapping["yj_code"]]?.takeIf { it.isNotBlank() },
            janCode = row[mapping["jan_code"]]?.takeIf { it.isNotBlank() },
            gtin = gtin,
            gtinSales = row[mapping["gtin_sales"]]?.takeIf { it.isNotBlank() },
            gtinCase = row[mapping["gtin_case"]]?.takeIf { it.isNotBlank() },
            year = row[mapping["year"]]?.toIntOrNull(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun normalizeGtin(value: String): String {
        val digits = value.filter(Char::isDigit)
        return when {
            digits.isEmpty() -> ""
            digits.length == 13 -> "0$digits"
            digits.length >= 14 -> digits.take(14)
            else -> ""
        }
    }
}
