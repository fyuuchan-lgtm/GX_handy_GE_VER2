package com.example.yakuzaiapp.domain.dispensing

sealed class ScanMatchResult {
    data class Success(val itemId: String, val drugName: String) : ScanMatchResult()
    data class NotInList(val drugName: String) : ScanMatchResult()
    data class AlreadyConfirmed(val drugName: String) : ScanMatchResult()
    data class PackingMachine(val drugName: String) : ScanMatchResult()
    data class PackageBarcodeNotSupported(val gtin: String) : ScanMatchResult()
    data class InvalidBarcodeFormat(val rawCode: String) : ScanMatchResult()
    data class UnregisteredGtin(val gtin: String) : ScanMatchResult()
}
