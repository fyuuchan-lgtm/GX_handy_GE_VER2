package com.example.yakuzaiapp.domain.jahis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JahisCompletenessCheckerTest {
    @Test
    fun completeWhenRpNumbersAreContinuousAndDrugUsagePairsExist() {
        val result = JahisCompletenessChecker.check(
            """
            JAHISTC01
            201,1,Drug A,1,錠,4,1111111111111
            301,1,1日1回朝食後,7,日分,1,1,
            201,2,Drug B,1,錠,4,2222222222222
            301,2,1日2回朝夕,7,日分,1,1,
            201,3,Drug C,1,錠,4,3333333333333
            301,3,1日3回朝昼夕,7,日分,1,1,
            """.trimIndent()
        )

        assertTrue(result is CheckResult.Complete)
        assertEquals(setOf(1, 2, 3), result.drugRpNumbers)
        assertEquals(setOf(1, 2, 3), result.usageRpNumbers)
    }

    @Test
    fun detectsMissingRpNumber() {
        val result = JahisCompletenessChecker.check(
            """
            JAHISTC01
            201,1,Drug A,1,錠,4,1111111111111
            301,1,1日1回朝食後,7,日分,1,1,
            201,2,Drug B,1,錠,4,2222222222222
            301,2,1日2回朝夕,7,日分,1,1,
            201,4,Drug D,1,錠,4,4444444444444
            301,4,1日1回夕食後,7,日分,1,1,
            """.trimIndent()
        )

        val incomplete = result as CheckResult.Incomplete
        assertTrue(incomplete.issues.contains(CheckIssue.MissingRp(listOf(3))))
    }

    @Test
    fun detectsMissingUsageForDrugRp() {
        val result = JahisCompletenessChecker.check(
            """
            JAHISTC01
            201,1,Drug A,1,錠,4,1111111111111
            """.trimIndent()
        )

        val incomplete = result as CheckResult.Incomplete
        assertTrue(incomplete.issues.contains(CheckIssue.MissingUsageForRp(1)))
    }

    @Test
    fun detectsMalformedDrugRecordEvenWhenRpNumberAndUsageExist() {
        val result = JahisCompletenessChecker.check(
            """
            JAHISTC01
            201,1,Drug A,1,錠,4,1111111111111
            301,1,1日1回朝食後,7,日分,1,1,
            201,2,Broken Drug
            301,2,1日1回就寝前,7,日分,1,1,
            201,3,Drug C,1,錠,4,3333333333333
            301,3,1日3回朝昼夕,7,日分,1,1,
            """.trimIndent()
        )

        val incomplete = result as CheckResult.Incomplete
        assertTrue(incomplete.issues.contains(CheckIssue.MalformedDrugRecord(2)))
    }

    @Test
    fun tc02CompleteWhenDrugFieldsUseTc02Layout() {
        val result = JahisCompletenessChecker.check(
            """
            JAHISTC02,1
            201,1,1,1,4,1111111111111,Drug A,1,,錠
            301,1,1日1回朝食後,7,日分,1,1,
            """.trimIndent()
        )

        assertTrue(result is CheckResult.Complete)
        assertEquals(setOf(1), result.drugRpNumbers)
        assertEquals(setOf(1), result.usageRpNumbers)
    }

    @Test
    fun tc02DetectsMalformedDrugRecordWhenTc02DrugNameIsMissing() {
        val result = JahisCompletenessChecker.check(
            """
            JAHISTC02,1
            201,1,1,1,4,1111111111111,,1,,錠
            301,1,1日1回朝食後,7,日分,1,1,
            """.trimIndent()
        )

        val incomplete = result as CheckResult.Incomplete
        assertTrue(incomplete.issues.contains(CheckIssue.MalformedDrugRecord(1)))
    }

    @Test
    fun detectsOrphanUsageRecord() {
        val result = JahisCompletenessChecker.check(
            """
            JAHISTC01
            301,1,1日1回朝食後,7,日分,1,1,
            """.trimIndent()
        )

        val incomplete = result as CheckResult.Incomplete
        assertTrue(incomplete.issues.contains(CheckIssue.OrphanUsageRecord(1)))
    }

    @Test
    fun detectsMissingHeader() {
        val result = JahisCompletenessChecker.check(
            """
            201,1,Drug A,1,錠,4,1111111111111
            301,1,1日1回朝食後,7,日分,1,1,
            """.trimIndent()
        )

        val incomplete = result as CheckResult.Incomplete
        assertTrue(incomplete.issues.contains(CheckIssue.MissingHeader))
    }
}
