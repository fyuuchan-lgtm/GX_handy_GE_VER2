package com.example.yakuzaiapp.util

/**
 * バーコード文字列から GTIN-14 を抽出する。
 *
 * - AI=01 プレフィックス付きの 16 桁は先頭 14 桁を返す
 * - 既に GTIN-14 の場合はそのまま返す
 * - GTIN-13 の場合は先頭に 0 を付けて GTIN-14 にする
 * - その他は null を返す
 */
fun normalizeGtin(rawCode: String): String? {
    val value = rawCode
        .map { ch ->
            when (ch) {
                in '０'..'９' -> '0' + (ch - '０')
                else -> ch
            }
        }
        .joinToString("")
        .filter(Char::isDigit)
    val candidate = when {
        value.startsWith("01") && value.length >= 16 -> value.substring(2, 16)
        value.length == 14 -> value
        value.length == 13 -> "0$value"
        else -> null
    }
    return candidate?.takeIf(::hasValidGtinCheckDigit)
}

private fun hasValidGtinCheckDigit(gtin: String): Boolean {
    if (gtin.isEmpty() || !gtin.all(Char::isDigit)) return false
    val checkDigit = gtin.last().digitToInt()
    var sum = 0
    var weight = 3
    for (i in gtin.length - 2 downTo 0) {
        sum += gtin[i].digitToInt() * weight
        weight = if (weight == 3) 1 else 3
    }
    val expected = (10 - (sum % 10)) % 10
    return checkDigit == expected
}

/**
 * GTIN-14 のうち、1〜8 で始まる包装・箱バーコードかどうかを判定する。
 *
 * - 14 桁のみを対象とする
 * - 先頭 0 はシート系、9 は予備コードとして false
 */
fun isPackageBarcode(gtin: String): Boolean {
    val value = gtin.trim()
    return value.length == 14 && value.all(Char::isDigit) && value.first() in '1'..'8'
}
