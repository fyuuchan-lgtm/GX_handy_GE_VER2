package com.example.yakuzaiapp.data.medis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class MedisAutoUpdateCoordinator(
    private val networkMonitor: NetworkMonitor,
    private val metadataStore: MedisUpdateMetadataStore,
    private val remoteDataSource: MedisRemoteDataSource,
    private val importer: MedisMasterImporter,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val running = AtomicBoolean(false)
    private val _state = MutableStateFlow<MedisAutoUpdateState>(MedisAutoUpdateState.Idle)
    val state: StateFlow<MedisAutoUpdateState> = _state.asStateFlow()

    fun maybeStartAutoUpdate(force: Boolean = false) {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                runUpdate(force)
            } finally {
                running.set(false)
            }
        }
    }

    fun clearTransientState() {
        if (_state.value !is MedisAutoUpdateState.Running) {
            _state.value = MedisAutoUpdateState.Idle
        }
    }

    private suspend fun runUpdate(force: Boolean) {
        val now = clockMillis()
        val metadata = metadataStore.read()
        val isInitialDownload = metadata.lastImportSuccessAt == 0L
        if (!force && now - metadata.lastHomepageAccessSuccessAt < CHECK_INTERVAL_MS) {
            _state.value = MedisAutoUpdateState.Idle
            return
        }

        if (!networkMonitor.hasValidatedInternet()) {
            _state.value = if (force) {
                MedisAutoUpdateState.Error("ネットワーク接続を確認できないため、データ更新を開始できません。")
            } else {
                MedisAutoUpdateState.Idle
            }
            return
        }

        try {
            _state.value = MedisAutoUpdateState.Running(
                phase = MedisAutoUpdateState.Phase.Checking,
                message = "MEDIS の最新版を確認中...",
                isInitialDownload = isInitialDownload,
            )
            val links = remoteDataSource.discoverLatestLinks()

            _state.value = MedisAutoUpdateState.Running(
                phase = MedisAutoUpdateState.Phase.Downloading,
                message = "MEDIS HOT をダウンロード中...",
                isInitialDownload = isInitialDownload,
            )
            val hotZipBytes = remoteDataSource.download(links.hotZipUrl)

            _state.value = MedisAutoUpdateState.Running(
                phase = MedisAutoUpdateState.Phase.Downloading,
                message = "販売名ファイルをダウンロード中...",
                isInitialDownload = isInitialDownload,
            )
            val salesBytes = remoteDataSource.download(links.salesFileUrl)

            val result = importer.importMasters(
                links = links,
                hotZipBytes = hotZipBytes,
                salesBytes = salesBytes,
                onProgress = { progress ->
                    _state.value = progress.copy(isInitialDownload = isInitialDownload)
                },
            )
            val completedAt = clockMillis()
            metadataStore.markHomepageAccessSuccess(completedAt)
            metadataStore.markImportSuccess(result, completedAt)
            _state.value = MedisAutoUpdateState.Idle
        } catch (e: Exception) {
            _state.value = MedisAutoUpdateState.Error(e.message ?: "データ更新に失敗しました。")
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
