package com.example.yakuzaiapp.util

object GtinExtractor {
    private val gtinRegex = Regex("""(?:\(|^)?01(\d{14})""")

    fun extract(rawText: String): String? {
        val cleaned = rawText.replace(" ", "").replace("-", "")
        val match = gtinRegex.find(cleaned) ?: return null
        return match.groupValues[1]
    }
}

