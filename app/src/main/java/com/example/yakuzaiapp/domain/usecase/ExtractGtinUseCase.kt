package com.example.yakuzaiapp.domain.usecase

import com.example.yakuzaiapp.util.GtinExtractor

class ExtractGtinUseCase {
    fun execute(rawText: String): String? = GtinExtractor.extract(rawText)
}

