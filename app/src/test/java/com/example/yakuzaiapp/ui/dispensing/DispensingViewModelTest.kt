package com.example.yakuzaiapp.ui.dispensing

import com.example.yakuzaiapp.data.jahis.DrugCodeType
import com.example.yakuzaiapp.data.jahis.JahisDrug
import com.example.yakuzaiapp.data.jahis.JahisPrescription
import com.example.yakuzaiapp.data.jahis.JahisRp
import com.example.yakuzaiapp.data.local.dao.DrugPreferenceDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.DrugPreference
import com.example.yakuzaiapp.data.repository.DrugPreferenceRepository
import com.example.yakuzaiapp.domain.dispensing.DispensingSession
import com.example.yakuzaiapp.domain.dispensing.ExpectedDrugItem
import com.example.yakuzaiapp.domain.dispensing.ExpectedListBuilderContract
import com.example.yakuzaiapp.domain.dispensing.ItemStatus
import com.example.yakuzaiapp.domain.dispensing.ScanMatchResult
import com.example.yakuzaiapp.repository.DrugMasterLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DispensingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successTurnsUncheckedItemIntoConfirmed() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup(
                "04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987732010087")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.Success)
        val updated = viewModel.session.value?.items?.first()
        assertEquals(ItemStatus.CONFIRMED, updated?.status)
        assertNotNull(updated?.checkedAt)
    }

    @Test
    fun successOnLastUncheckedItemSetsAllCheckedWithoutAutoEvent() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup(
                "04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987732010087")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.Success)
        assertTrue(viewModel.isAllChecked.value)
    }

    @Test
    fun sameGtinCanConfirmTwoUncheckedRowsWithinDebounceWindow() = runTest(dispatcher) {
        val first = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val second = expectedItem(
            id = "item-2",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(first, second),
            lookup = fakeLookup(
                "04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onPtpScanned("0104987732010087")
        advanceUntilIdle()
        viewModel.onPtpScanned("0104987732010087")
        advanceUntilIdle()

        assertEquals(ItemStatus.CONFIRMED, viewModel.session.value?.items?.first { it.id == "item-1" }?.status)
        assertEquals(ItemStatus.CONFIRMED, viewModel.session.value?.items?.first { it.id == "item-2" }?.status)
        assertTrue(viewModel.isAllChecked.value)
    }

    @Test
    fun packageGtinCanConfirmUncheckedItem() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup(
                "14987732010084" to drugMaster("14987732010084", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("14987732010084")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.Success)
        assertEquals(ItemStatus.CONFIRMED, viewModel.session.value?.items?.first()?.status)
    }

    @Test
    fun drugCodeFallbackCanConfirmUncheckedItemWhenMasterYjDiffers() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup(
                "04987376861687" to drugMaster(
                    gtin = "04987376861687",
                    yjCode = "2354003F2464",
                    drugName = "sennoside12mg NIG",
                    drugCode = "2354003F2014"
                )
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987376861687")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.Success)
        assertEquals(ItemStatus.CONFIRMED, viewModel.session.value?.items?.first()?.status)
    }

    @Test
    fun notInListEmitsNotInList() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ999",
            drugName = "Different Drug",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup(
                "04987224716428" to drugMaster("04987224716428", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987224716428")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.NotInList)
        assertEquals(ItemStatus.UNCHECKED, viewModel.session.value?.items?.first()?.status)
    }

    @Test
    fun alreadyConfirmedEmitsAlreadyConfirmed() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.CONFIRMED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup(
                "04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987732010087")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.AlreadyConfirmed)
        assertEquals(ItemStatus.CONFIRMED, viewModel.session.value?.items?.first()?.status)
    }

    @Test
    fun duplicateYjScanConfirmsUncheckedRowBeforeReportingAlreadyConfirmed() = runTest(dispatcher) {
        val confirmed = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.CONFIRMED
        )
        val unchecked = expectedItem(
            id = "item-2",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(confirmed, unchecked),
            lookup = fakeLookup(
                "04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987732010087")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.Success)
        assertEquals(ItemStatus.CONFIRMED, viewModel.session.value?.items?.first { it.id == "item-2" }?.status)
    }

    @Test
    fun duplicateDrugCodeFallbackScanConfirmsUncheckedRowBeforeReportingAlreadyConfirmed() = runTest(dispatcher) {
        val confirmed = expectedItem(
            id = "item-1",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg",
            status = ItemStatus.CONFIRMED
        )
        val unchecked = expectedItem(
            id = "item-2",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(confirmed, unchecked),
            lookup = fakeLookup(
                "04987376861687" to drugMaster(
                    gtin = "04987376861687",
                    yjCode = "2354003F2464",
                    drugName = "sennoside12mg NIG",
                    drugCode = "2354003F2014"
                )
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987376861687")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.Success)
        assertEquals(ItemStatus.CONFIRMED, viewModel.session.value?.items?.first { it.id == "item-2" }?.status)
    }

    @Test
    fun packingMachineEmitsPackingMachine() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.PACKING_MACHINE
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup(
                "04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")
            )
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("0104987732010087")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.PackingMachine)
        assertEquals(ItemStatus.PACKING_MACHINE, viewModel.session.value?.items?.first()?.status)
    }

    @Test
    fun unregisteredGtinEmitsUnregisteredGtin() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup()
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        val feedback = async { viewModel.scanFeedback.first() }
        viewModel.onPtpScanned("00000000000000")
        advanceUntilIdle()

        assertTrue(feedback.await() is ScanMatchResult.UnregisteredGtin)
        assertEquals(ItemStatus.UNCHECKED, viewModel.session.value?.items?.first()?.status)
    }

    @Test
    fun packingPreferenceMakesInitialStatusPackingMachine() = runTest(dispatcher) {
        val builder = com.example.yakuzaiapp.domain.dispensing.ExpectedListBuilder(
            drugMasterDao = fakeDrugMasterDao(
                drugMaster("04987732010087", "YJ123", "Drug A")
            ),
            drugPreferenceRepository = fakePreferenceRepository(
                "YJ123" to true
            )
        )

        val session = builder.build(
            JahisPrescription(
                version = "JAHISTC12",
                patientName = "Patient A",
                patientGender = "1",
                patientBirthDate = "19800101",
            dispensingDate = "20260606",
            pharmacyName = "Test Pharmacy",
            prescribingHospital = "Test Hospital",
            doctorName = null,
            department = null,
            rps = listOf(
                    JahisRp(
                        rpNumber = 1,
                        drugs = listOf(
                            JahisDrug(
                                rpNumber = 1,
                                drugCodeType = DrugCodeType.YJ,
                                drugCode = "YJ123",
                                drugName = "Drug A",
                                quantity = "1",
                                unit = "TAB"
                            )
                        ),
                        usage = "once daily"
                    )
                )
            )
        )

        assertEquals(ItemStatus.PACKING_MACHINE, session.items.first().status)
    }

    @Test
    fun longPressUncheckedShowsDialogAndConfirmsAsPackingMachine() = runTest(dispatcher) {
        val preference = fakePreferenceRepositoryHarness()
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup("04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")),
            drugPreferenceRepository = preference.repository
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onLongPressItem(item.id)
        assertEquals(ItemStatus.UNCHECKED, viewModel.longPressDialog.value?.currentStatus)

        viewModel.onLongPressConfirm(item.id)
        advanceUntilIdle()

        assertEquals(ItemStatus.PACKING_MACHINE, viewModel.session.value?.items?.first()?.status)
        assertEquals(true, preference.store["YJ123"])
        assertTrue(viewModel.isAllChecked.value)
        assertNotNull(viewModel.session.value)
    }

    @Test
    fun longPressPackingMachineRemovesPreferenceAndReturnsUnchecked() = runTest(dispatcher) {
        val preference = fakePreferenceRepositoryHarness("YJ123" to true)
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.PACKING_MACHINE
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup("04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")),
            drugPreferenceRepository = preference.repository
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onLongPressItem(item.id)
        assertEquals(ItemStatus.PACKING_MACHINE, viewModel.longPressDialog.value?.currentStatus)

        viewModel.onLongPressConfirm(item.id)
        advanceUntilIdle()

        assertEquals(ItemStatus.UNCHECKED, viewModel.session.value?.items?.first()?.status)
        assertEquals(null, preference.store["YJ123"])
    }

    @Test
    fun itemClickUncheckedMarksPackingMachineAndPersistsPreference() = runTest(dispatcher) {
        val preference = fakePreferenceRepositoryHarness()
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup("04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")),
            drugPreferenceRepository = preference.repository
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onItemClick(item.id)
        advanceUntilIdle()

        assertEquals(ItemStatus.PACKING_MACHINE, viewModel.session.value?.items?.first()?.status)
        assertEquals(true, preference.store["YJ123"])
    }

    @Test
    fun itemClickPackingMachineReturnsUncheckedAndRemovesPreference() = runTest(dispatcher) {
        val preference = fakePreferenceRepositoryHarness("YJ123" to true)
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.PACKING_MACHINE
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup("04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A")),
            drugPreferenceRepository = preference.repository
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onItemClick(item.id)
        advanceUntilIdle()

        assertEquals(ItemStatus.UNCHECKED, viewModel.session.value?.items?.first()?.status)
        assertEquals(null, preference.store["YJ123"])
    }

    @Test
    fun longPressConfirmedEmitsAlreadyConfirmed() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.CONFIRMED
        )
        val viewModel = createViewModel(
            session = sessionOf(item),
            lookup = fakeLookup("04987732010087" to drugMaster("04987732010087", "YJ123", "Drug A"))
        )

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onLongPressItem(item.id)
        advanceUntilIdle()

        assertTrue(viewModel.scanFeedback.replayCache.last() is ScanMatchResult.AlreadyConfirmed)
    }

    @Test
    fun allConfirmedSessionSetsAllCheckedTrue() = runTest(dispatcher) {
        val items = listOf(
            expectedItem("item-1", "YJ123", "Drug A", ItemStatus.CONFIRMED),
            expectedItem("item-2", "YJ456", "Drug B", ItemStatus.PACKING_MACHINE)
        )
        val viewModel = createViewModel(session = sessionOf(*items.toTypedArray()), lookup = fakeLookup())

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        assertTrue(viewModel.isAllChecked.value)
        assertEquals(0, viewModel.uncheckedCount.value)
        assertTrue(viewModel.uncheckedDrugNames.value.isEmpty())
    }

    @Test
    fun onCompleteClickWithUncheckedItemsShowsWarningDialog() = runTest(dispatcher) {
        val items = listOf(
            expectedItem("item-1", "YJ123", "Drug A", ItemStatus.CONFIRMED),
            expectedItem("item-2", "YJ456", "Drug B", ItemStatus.UNCHECKED),
            expectedItem("item-3", "YJ789", "Drug C", ItemStatus.UNCHECKED)
        )
        val viewModel = createViewModel(session = sessionOf(*items.toTypedArray()), lookup = fakeLookup())

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onCompleteClick()

        val dialog = viewModel.completionDialog.value
        assertTrue(dialog is CompletionDialogState.HasUnchecked)
        dialog as CompletionDialogState.HasUnchecked
        assertEquals(2, dialog.count)
        assertEquals(listOf("Drug B", "Drug C"), dialog.names)
    }

    @Test
    fun onCompleteClickWithAllCheckedShowsCompletedDialog() = runTest(dispatcher) {
        val items = listOf(
            expectedItem("item-1", "YJ123", "Drug A", ItemStatus.CONFIRMED),
            expectedItem("item-2", "YJ456", "Drug B", ItemStatus.PACKING_MACHINE)
        )
        val viewModel = createViewModel(session = sessionOf(*items.toTypedArray()), lookup = fakeLookup())

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onCompleteClick()

        assertTrue(viewModel.completionDialog.value is CompletionDialogState.AllCompleted)
    }

    @Test
    fun completionConfirmClearsSession() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(session = sessionOf(item), lookup = fakeLookup())

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onCompleteClick()
        viewModel.onCompletionConfirm()
        advanceUntilIdle()

        assertEquals(null, viewModel.session.value)
        assertTrue(viewModel.completionDialog.value is CompletionDialogState.Hidden)
    }

    @Test
    fun completionDismissKeepsSession() = runTest(dispatcher) {
        val item = expectedItem(
            id = "item-1",
            yjCode = "YJ123",
            drugName = "Drug A",
            status = ItemStatus.UNCHECKED
        )
        val viewModel = createViewModel(session = sessionOf(item), lookup = fakeLookup())

        viewModel.onQrScanned("ignored")
        advanceUntilIdle()

        viewModel.onCompleteClick()
        viewModel.onCompletionDismiss()

        assertNotNull(viewModel.session.value)
        assertTrue(viewModel.completionDialog.value is CompletionDialogState.Hidden)
    }

    private fun createViewModel(
        session: DispensingSession,
        lookup: DrugMasterLookup,
        drugPreferenceRepository: DrugPreferenceRepository = fakePreferenceRepository()
    ): DispensingViewModel {
        return DispensingViewModel(
            expectedListBuilder = object : ExpectedListBuilderContract {
                override suspend fun build(prescription: JahisPrescription): DispensingSession {
                    return session
                }
            },
            drugMasterLookup = lookup,
            drugPreferenceRepository = drugPreferenceRepository
        )
    }

    private fun sessionOf(vararg items: ExpectedDrugItem): DispensingSession {
        return DispensingSession(
            id = "session-1",
            createdAt = 0L,
            patientName = "Patient A",
            patientGender = "1",
            patientBirthDate = "19800101",
            dispensingDate = "20260606",
            pharmacyName = "Test Pharmacy",
            prescribingHospital = "Test Hospital",
            doctorName = null,
            department = null,
            items = items.toList()
        )
    }

    private fun expectedItem(
        id: String,
        yjCode: String,
        drugName: String,
        status: ItemStatus
    ): ExpectedDrugItem {
        return ExpectedDrugItem(
            id = id,
            rpNumber = 1,
            drugCodeType = DrugCodeType.YJ,
            drugCode = yjCode,
            drugName = drugName,
            quantity = "1",
            unit = "TAB",
            matchedYjCode = yjCode,
            matchedGtin = "04987732010087",
            matchedDrugName = drugName,
            status = status,
            checkedAt = if (status == ItemStatus.CONFIRMED) 123L else null,
            isGeneric = false
        )
    }

    private fun drugMaster(
        gtin: String,
        yjCode: String,
        drugName: String,
        drugCode: String = yjCode
    ): DrugMaster {
        return DrugMaster(
            hot13 = gtin,
            drugCode = drugCode,
            drugName = drugName,
            yjCode = yjCode,
            gtin = gtin
        )
    }

    private fun fakeLookup(vararg entries: Pair<String, DrugMaster>): DrugMasterLookup {
        val map = entries.toMap()
        return object : DrugMasterLookup {
            override suspend fun findByAnyGtin(code: String): DrugMaster? = map[code]
            override suspend fun findByGtin(gtin: String): DrugMaster? = map[gtin]
        }
    }

    private fun fakePreferenceRepository(
        vararg enabledEntries: Pair<String, Boolean>
    ): DrugPreferenceRepository {
        return fakePreferenceRepositoryHarness(*enabledEntries).repository
    }

    private fun fakePreferenceRepositoryHarness(
        vararg enabledEntries: Pair<String, Boolean>
    ): PreferenceHarness {
        val store = enabledEntries.toMap().toMutableMap()
        val dao = object : DrugPreferenceDao {
            override suspend fun upsert(pref: DrugPreference) {
                store[pref.yjCode] = pref.defaultPackingMachine
            }

            override suspend fun findByYjCode(yjCode: String): DrugPreference? {
                val enabled = store[yjCode] ?: return null
                return DrugPreference(yjCode = yjCode, defaultPackingMachine = enabled, updatedAt = 0L)
            }

            override suspend fun getAllPackingMachineDrugs(): List<DrugPreference> {
                return store.filterValues { it }.map { (yjCode, enabled) ->
                    DrugPreference(yjCode = yjCode, defaultPackingMachine = enabled, updatedAt = 0L)
                }
            }

            override suspend fun deleteByYjCode(yjCode: String) {
                store.remove(yjCode)
            }

            override suspend fun count(): Int = store.size
        }
        return PreferenceHarness(DrugPreferenceRepository(dao), store)
    }

    private fun fakeDrugMasterDao(vararg masters: DrugMaster) =
        object : com.example.yakuzaiapp.data.local.dao.DrugMasterDao {
            override suspend fun insertAll(items: List<DrugMaster>) {}
            override suspend fun upsertAll(items: List<DrugMaster>) {}
            override suspend fun deleteAll() {}
            override suspend fun deleteImported() {}
            override suspend fun deleteUserRegistered(hot13: String) {}
            override fun observeUserRegistered(): kotlinx.coroutines.flow.Flow<List<DrugMaster>> =
                kotlinx.coroutines.flow.flowOf(emptyList())
            override fun observeAll() = kotlinx.coroutines.flow.flowOf(masters.toList())
            override fun searchByKeyword(keyword: String) = kotlinx.coroutines.flow.flowOf(masters.toList())
            override suspend fun findByExactName(name: String): List<DrugMaster> =
                masters.filter { it.drugName == name }

            override suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster> =
                masters.filter { it.drugName == name || it.packageName == name }

            override suspend fun findByCore(core: String): List<DrugMaster> =
                masters.filter { master ->
                    master.drugName.contains(core) ||
                        master.drugNameKana1?.contains(core) == true ||
                        master.drugNameKana2?.contains(core) == true ||
                        master.drugNameKana3?.contains(core) == true ||
                        master.alias?.contains(core) == true
                }

            override suspend fun findByCoreNormalized(
                core: String,
                coreFullWidth: String,
                coreSmallKanaNormalized: String
            ): List<DrugMaster> =
                (findByCore(core) + findByCore(coreFullWidth) + findByCore(coreSmallKanaNormalized))
                    .distinctBy { it.hot13 }

            override suspend fun findAllForLevenshtein(limit: Int): List<DrugMaster> =
                masters.take(limit)

            override suspend fun findByAnyGtin(code: String): DrugMaster? = masters.firstOrNull { it.gtin == code || it.hot13 == code || it.gtinSales == code || it.gtinCase == code }
            override suspend fun findByGtin(gtin: String): DrugMaster? = masters.firstOrNull { it.gtin == gtin }
            override suspend fun findByYjCode(yjCode: String): DrugMaster? =
                masters.firstOrNull { it.yjCode == yjCode || it.drugCode == yjCode }
            override suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = null
            override suspend fun findByCaseGtin(gtin: String): DrugMaster? = null
            override suspend fun count(): Int = masters.size
        }

    private data class PreferenceHarness(
        val repository: DrugPreferenceRepository,
        val store: MutableMap<String, Boolean>
    )
}

