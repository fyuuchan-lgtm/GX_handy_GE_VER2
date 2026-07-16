package com.example.yakuzaiapp.domain.dispensing

import com.example.yakuzaiapp.data.jahis.DrugCodeType
import com.example.yakuzaiapp.data.jahis.JahisDrug
import com.example.yakuzaiapp.data.jahis.JahisPrescription
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.repository.DrugPreferenceRepository
import com.example.yakuzaiapp.util.toKatakana
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.util.UUID

private const val TAG = "ExpectedListBuilder"
private val BRAND_SUFFIXES = listOf("明治", "トーワ", "EE", "サンド", "ファイザー")
private val IS_ANDROID_RUNTIME = runCatching {
    val vmName = System.getProperty("java.vm.name").orEmpty()
    vmName.contains("Dalvik", ignoreCase = true) || vmName.contains("ART", ignoreCase = true)
}.getOrDefault(false)

interface ExpectedListBuilderContract {
    suspend fun build(prescription: JahisPrescription): DispensingSession
}

class ExpectedListBuilder(
    private val drugMasterDao: DrugMasterDao,
    private val drugPreferenceRepository: DrugPreferenceRepository
) : ExpectedListBuilderContract {
    override suspend fun build(prescription: JahisPrescription): DispensingSession {
        val items = prescription.rps.flatMap { rp ->
            rp.drugs.map { drug ->
                val isGeneric = drug.drugCodeType == DrugCodeType.GENERIC_MHLW ||
                    drug.drugName.startsWith("\u3010\u822c\u3011")
                val matchedMaster = if (isGeneric) null else matchDrug(drug)
                val matchedYjCode = matchedMaster?.yjCode?.takeIf { it.isNotBlank() } ?: matchedMaster?.drugCode
                val initialStatus = if (
                    matchedYjCode != null &&
                    drugPreferenceRepository.isPackingMachine(matchedYjCode)
                ) {
                    ItemStatus.PACKING_MACHINE
                } else {
                    ItemStatus.UNCHECKED
                }

                ExpectedDrugItem(
                    id = UUID.randomUUID().toString(),
                    rpNumber = rp.rpNumber,
                    drugCodeType = drug.drugCodeType,
                    drugCode = drug.drugCode,
                    drugName = drug.drugName,
                    quantity = drug.quantity,
                    unit = drug.unit,
                    totalQuantityDisplay = PrescriptionQuantityCalculator.calculate(
                        quantity = drug.quantity,
                        unit = drug.unit,
                        dispensingQuantity = rp.dispensingQuantity,
                        dosageFormCode = rp.dosageFormCode
                    ),
                    matchedYjCode = matchedYjCode,
                    matchedGtin = matchedMaster?.gtin ?: matchedMaster?.hot13,
                    matchedDrugName = matchedMaster?.drugName,
                    status = initialStatus,
                    checkedAt = null,
                    isGeneric = isGeneric
                )
            }
        }

        return DispensingSession(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            patientName = prescription.patientName,
            patientGender = prescription.patientGender,
            patientBirthDate = prescription.patientBirthDate,
            dispensingDate = prescription.dispensingDate,
            pharmacyName = prescription.pharmacyName,
            prescribingHospital = prescription.prescribingHospital,
            doctorName = prescription.doctorName,
            department = prescription.department,
            items = items
        )
    }

    private suspend fun matchDrug(drug: JahisDrug): DrugMaster? {
        val normalizedLookupCode = normalizeYjCode(drug.drugCode)
        debugLog("ExpectedListBuilder: input codeType=${drug.drugCodeType} hasCode=${normalizedLookupCode != null}")

        val matchedByCode = normalizedLookupCode?.let { code ->
            val hit = drugMasterDao.findByYjCode(code)
            debugLog("ExpectedListBuilder: findByYjCode hit=${hit != null}")
            hit
        }

        if (matchedByCode != null) {
            debugLog(
                "matchByCode codeType=${drug.drugCodeType} matched=true"
            )
            return matchedByCode
        }

        debugLog("ExpectedListBuilder: fallback path entered")
        val matched = findByDrugName(drug.drugName)
        debugLog(
            "matchByName codeType=${drug.drugCodeType} matched=${matched != null}"
        )
        return matched
    }

    private fun normalizeYjCode(value: String?): String? {
        return value
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("""[\s-]+"""), "")
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun findByDrugName(drugName: String): DrugMaster? {
        val target = drugName.trim()
        if (target.isBlank()) return null
        val normalizedTarget = normalizeDrugNameForMatching(target)
        return drugMasterDao.searchByKeyword(normalizedTarget.toKatakana()).first()
            .firstOrNull { master -> matchesByName(master, normalizedTarget) }
    }

    private fun matchesByName(master: DrugMaster, target: String): Boolean {
        val normalizedTarget = normalizeDrugNameForMatching(target)
        if (normalizedTarget.isBlank()) return false
        val candidates = buildList {
            add(master.drugName)
            master.packageName?.let(::add)
            master.drugNameKana1?.let(::add)
            master.drugNameKana2?.let(::add)
            master.drugNameKana3?.let(::add)
            master.alias?.let(::add)
        }
        return candidates.any { candidate ->
            val normalizedCandidate = normalizeDrugNameForMatching(candidate)
            normalizedCandidate.contains(normalizedTarget, ignoreCase = true) ||
                normalizedTarget.contains(normalizedCandidate, ignoreCase = true)
        }
    }

    private fun normalizeDrugNameForMatching(value: String): String {
        return stripBrandSuffixes(
            Normalizer.normalize(value, Normalizer.Form.NFKC)
        )
            .replace("\u3000", " ")
            .replace("\u2605", "")
            .replace("\u3010\u7c21\u3011", "")
            .replace("\u3010\u822c\u3011", "")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun stripBrandSuffixes(value: String): String {
        var normalized = value
        normalized = normalized.replace(Regex("""[「『（(][^」』）)]+[」』）)]$"""), "")
        BRAND_SUFFIXES.forEach { suffix ->
            normalized = normalized.replace(Regex("""(?:\s|　)*${Regex.escape(suffix)}$"""), "")
        }
        return normalized
    }

    private fun debugLog(message: String) {
        if (IS_ANDROID_RUNTIME) {
            android.util.Log.d(TAG, message)
        } else {
            println("$TAG: $message")
        }
    }
}
