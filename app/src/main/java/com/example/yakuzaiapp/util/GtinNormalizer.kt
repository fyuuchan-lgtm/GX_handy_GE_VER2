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

/**
 * マスター照合用のバーコードを正規化する。
 *
 * 市販品のGTINはGTIN-14へ正規化し、院内製剤や材料で使われる施設独自コードは
 * 読み取った文字列をそのまま照合キーとして扱う。
 */
fun normalizeMasterBarcode(rawCode: String): String? {
    normalizeGtin(rawCode)?.let { return it }
    val value = rawCode.trim()
    if (value.length !in 3..64 || value.any(Char::isISOControl)) return null

    val digits = value.filter(Char::isDigit)
    val looksLikeInvalidGtin = value.all { it.isDigit() || it.isWhitespace() || it == '(' || it == ')' } &&
        (digits.length == 13 || digits.length == 14 || (digits.startsWith("01") && digits.length >= 16))
    if (looksLikeInvalidGtin) return null

    return value.takeIf { digits.isNotEmpty() }
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
