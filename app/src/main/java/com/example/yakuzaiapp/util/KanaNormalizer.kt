package com.example.yakuzaiapp.util

/**
 * ひらがなをカタカナに変換する拡張関数。
 * 検索キーワードの正規化に使用。
 * 例: "あむろじぴん" -> "アムロジピン"
 */
fun String.toKatakana(): String = buildString {
    for (ch in this@toKatakana) {
        val code = ch.code
        if (code in 0x3041..0x3096) {
            append((code + 0x60).toChar())
        } else {
            append(ch)
        }
    }
}
