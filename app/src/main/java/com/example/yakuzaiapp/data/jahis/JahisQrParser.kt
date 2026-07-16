package com.example.yakuzaiapp.data.jahis

import android.util.Log
import java.nio.charset.Charset

object JahisQrParser {
    private const val TAG = "JahisQrParser"
    private val VERSION_REGEX = Regex("""JAHISTC\d{2}""")

    fun parse(rawText: String): JahisPrescription {
        val normalized = rawText
            .replace("\u001A", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val lines = normalized
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()

        val version = lines.firstNotNullOfOrNull { VERSION_REGEX.find(it.substringBefore(','))?.value } ?: "UNKNOWN"
        val state = MutablePrescription(version = version)
        val rpMap = linkedMapOf<Int, MutableRp>()

        lines.forEach { line ->
            if (line.startsWith("JAHISTC")) return@forEach

            val cols = line.split(',')
                .map { it.trim() }

            val recordNo = cols.firstOrNull()?.toIntOrNull()
            if (recordNo == null) {
                warn("Skip invalid record")
                return@forEach
            }

            try {
                val requiredFieldCount = requiredFieldCount(recordNo, state.version)
                if (requiredFieldCount != null && cols.size < requiredFieldCount) {
                    warn("Record $recordNo has insufficient fields: ${cols.size}, expected: $requiredFieldCount")
                    return@forEach
                }

                when (recordNo) {
                    1 -> parsePatient(cols, state)
                    5 -> state.dispensingDate = cols.getOrNull(1)?.takeIf { it.isNotBlank() }
                    11 -> state.pharmacyName = cols.getOrNull(1)?.takeIf { it.isNotBlank() }
                    51 -> state.prescribingHospital = cols.getOrNull(1)?.takeIf { it.isNotBlank() }
                    55 -> parseDoctor(cols, state)
                    201 -> parseDrug(cols, state.version, rpMap)
                    301 -> parseUsage(cols, rpMap)
                    else -> warn("Skip unsupported recordNo=$recordNo")
                }
            } catch (_: Exception) {
                warn("Skip recordNo=$recordNo due to parse error")
            }
        }

        val rps = rpMap.values
            .sortedBy { it.rpNumber }
            .map { it.toImmutable() }

        return JahisPrescription(
            version = state.version,
            patientName = state.patientName,
            patientGender = state.patientGender,
            patientBirthDate = state.patientBirthDate,
            dispensingDate = state.dispensingDate,
            pharmacyName = state.pharmacyName,
            prescribingHospital = state.prescribingHospital,
            doctorName = state.doctorName,
            department = state.department,
            rps = rps,
        )
    }

    fun parseFromBytes(
        bytes: ByteArray,
        charset: Charset = Charset.forName("Shift_JIS")
    ): JahisPrescription {
        val rawText = String(bytes, charset)
        return parse(rawText)
    }

    private fun parsePatient(cols: List<String>, state: MutablePrescription) {
        state.patientName = cols.getOrNull(1)?.takeIf { it.isNotBlank() }
        state.patientGender = cols.getOrNull(2)?.takeIf { it.isNotBlank() }
        state.patientBirthDate = cols.getOrNull(3)?.takeIf { it.isNotBlank() }
    }

    private fun parseDoctor(cols: List<String>, state: MutablePrescription) {
        state.doctorName = cols.getOrNull(1)?.takeIf { it.isNotBlank() }
        state.department = cols.getOrNull(2)?.takeIf { it.isNotBlank() }
    }

    private fun parseDrug(
        cols: List<String>,
        version: String,
        rpMap: MutableMap<Int, MutableRp>
    ) {
        val rpNumber = cols.getOrNull(1)?.toIntOrNull()
        if (rpNumber == null) {
            warn("Skip drug record without rpNumber")
            return
        }

        val fields = drugFields(cols, version)
        val drugCodeType = DrugCodeType.fromCode(fields.drugCodeTypeCode.orEmpty())
        val drugCode = fields.drugCode?.takeIf { it.isNotBlank() }
        val drugName = fields.drugName?.takeIf { it.isNotBlank() }
        val quantity = fields.quantity?.takeIf { it.isNotBlank() }
        val unit = fields.unit?.takeIf { it.isNotBlank() }
        val genericName = fields.genericName?.takeIf { it.isNotBlank() }
        val genericCodeType = fields.genericCodeType?.takeIf { it.isNotBlank() }
        val genericCode = fields.genericCode?.takeIf { it.isNotBlank() }

        if (drugName.isNullOrBlank() || quantity.isNullOrBlank() || unit.isNullOrBlank()) {
            warn("Skip drug record with missing fields")
            return
        }

        val rp = rpMap.getOrPut(rpNumber) { MutableRp(rpNumber) }
        rp.drugs += JahisDrug(
            rpNumber = rpNumber,
            drugCodeType = drugCodeType,
            drugCode = drugCode,
            drugName = drugName,
            quantity = quantity,
            unit = unit,
            genericName = genericName,
            genericCodeType = genericCodeType,
            genericCode = genericCode,
        )
        Log.d(TAG, "Parsed drug record")
    }

    private fun parseUsage(cols: List<String>, rpMap: MutableMap<Int, MutableRp>) {
        val rpNumber = cols.getOrNull(1)?.toIntOrNull()
        if (rpNumber == null) {
            warn("Skip usage record without rpNumber")
            return
        }
        val usage = cols.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: cols.getOrNull(3)?.takeIf { it.isNotBlank() }
        val rp = rpMap.getOrPut(rpNumber) { MutableRp(rpNumber) }
        rp.usage = usage
        rp.dispensingQuantity = cols.getOrNull(3)?.takeIf { it.isNotBlank() }
        rp.dispensingUnit = cols.getOrNull(4)?.takeIf { it.isNotBlank() }
        rp.dosageFormCode = cols.getOrNull(5)?.takeIf { it.isNotBlank() }
    }

    private fun drugFields(cols: List<String>, version: String): DrugFields {
        return if (isTc01Style(version)) {
            DrugFields(
                drugCodeTypeCode = cols.getOrNull(5),
                drugCode = cols.getOrNull(6),
                drugName = cols.getOrNull(2),
                quantity = cols.getOrNull(3),
                unit = cols.getOrNull(4),
            )
        } else {
            DrugFields(
                drugCodeTypeCode = cols.getOrNull(4),
                drugCode = cols.getOrNull(5),
                drugName = cols.getOrNull(6),
                quantity = cols.getOrNull(7),
                unit = cols.getOrNull(9) ?: cols.getOrNull(8),
                genericName = cols.getOrNull(10),
                genericCodeType = cols.getOrNull(11),
                genericCode = cols.getOrNull(12),
            )
        }
    }

    private fun requiredFieldCount(recordNo: Int, version: String): Int? {
        return when (recordNo) {
            1 -> 4
            5 -> 2
            11 -> 2
            51 -> 2
            55 -> 3
            201 -> if (isTc01Style(version)) 7 else 10
            301 -> 3
            else -> null
        }
    }

    private fun isTc01Style(version: String): Boolean {
        return version == "JAHISTC01" || version == "JAHISTC07"
    }

    private fun warn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
            println("$TAG: $message")
        }
    }

    private data class MutablePrescription(
        var version: String,
        var patientName: String? = null,
        var patientGender: String? = null,
        var patientBirthDate: String? = null,
        var dispensingDate: String? = null,
        var pharmacyName: String? = null,
        var prescribingHospital: String? = null,
        var doctorName: String? = null,
        var department: String? = null
    )

    private data class DrugFields(
        val drugCodeTypeCode: String?,
        val drugCode: String?,
        val drugName: String?,
        val quantity: String?,
        val unit: String?,
        val genericName: String? = null,
        val genericCodeType: String? = null,
        val genericCode: String? = null
    )

    private data class MutableRp(
        val rpNumber: Int,
        val drugs: MutableList<JahisDrug> = mutableListOf(),
        var usage: String? = null,
        var dispensingQuantity: String? = null,
        var dispensingUnit: String? = null,
        var dosageFormCode: String? = null
    ) {
        fun toImmutable(): JahisRp = JahisRp(
            rpNumber = rpNumber,
            drugs = drugs.toList(),
            usage = usage,
            dispensingQuantity = dispensingQuantity,
            dispensingUnit = dispensingUnit,
            dosageFormCode = dosageFormCode
        )
    }
}
