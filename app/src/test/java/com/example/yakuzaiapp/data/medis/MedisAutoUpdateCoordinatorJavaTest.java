package com.example.yakuzaiapp.data.medis;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MedisAutoUpdateCoordinatorJavaTest {
    @Test
    public void autoUpdateSkipsWhenHomepageAccessIsRecent() {
        Fixture fixture = new Fixture(10_000L, true);
        fixture.metadata.metadata = new MedisUpdateMetadata(9_000L, 0L, null, null, null, null);

        fixture.coordinator.maybeStartAutoUpdate(false);

        assertEquals(0, fixture.remote.discoverCount);
        assertTrue(fixture.coordinator.getState().getValue() instanceof MedisAutoUpdateState.Idle);
    }

    @Test
    public void manualUpdateBypassesThrottleButStillImportsSafely() {
        Fixture fixture = new Fixture(10_000L, true);
        fixture.metadata.metadata = new MedisUpdateMetadata(9_000L, 0L, null, null, null, null);

        fixture.coordinator.maybeStartAutoUpdate(true);

        assertEquals(1, fixture.remote.discoverCount);
        assertEquals(2, fixture.remote.downloadCount);
        assertEquals(1, fixture.importer.importCount);
        assertEquals("20260531", fixture.metadata.metadata.getHotVersionDate());
        assertEquals("20260615", fixture.metadata.metadata.getSalesVersionDate());
        assertTrue(fixture.coordinator.getState().getValue() instanceof MedisAutoUpdateState.Completed);
    }

    @Test
    public void manualUpdateDoesNotBypassNetworkRequirement() {
        Fixture fixture = new Fixture(10_000L, false);

        fixture.coordinator.maybeStartAutoUpdate(true);

        assertEquals(0, fixture.remote.discoverCount);
        assertEquals(0L, fixture.metadata.metadata.getLastHomepageAccessSuccessAt());
        assertTrue(fixture.coordinator.getState().getValue() instanceof MedisAutoUpdateState.Error);
    }

    @Test
    public void concurrentStartCallsShareSingleJob() throws Exception {
        Fixture fixture = new Fixture(10_000L, true, true);

        fixture.coordinator.maybeStartAutoUpdate(true);
        fixture.coordinator.maybeStartAutoUpdate(true);
        Thread.sleep(100L);

        assertEquals(1, fixture.remote.discoverCount);
        Thread.sleep(300L);
        assertEquals(1, fixture.importer.importCount);
    }

    private static final class Fixture {
        final FakeMetadataStore metadata = new FakeMetadataStore();
        final FakeRemoteDataSource remote = new FakeRemoteDataSource();
        final FakeImporter importer = new FakeImporter();
        final MedisAutoUpdateCoordinator coordinator;

        Fixture(long now, boolean hasNetwork) {
            this(now, hasNetwork, false);
        }

        Fixture(long now, boolean hasNetwork, boolean async) {
            remote.slowDiscovery = async;
            CoroutineScope scope = CoroutineScopeKt.CoroutineScope(
                    async ? Dispatchers.getDefault() : Dispatchers.getUnconfined()
            );
            coordinator = new MedisAutoUpdateCoordinator(
                    new FakeNetworkMonitor(hasNetwork),
                    metadata,
                    remote,
                    importer,
                    () -> now,
                    scope
            );
        }
    }

    private static final class FakeNetworkMonitor implements NetworkMonitor {
        private final boolean hasNetwork;

        FakeNetworkMonitor(boolean hasNetwork) {
            this.hasNetwork = hasNetwork;
        }

        @Override
        public boolean hasValidatedInternet() {
            return hasNetwork;
        }
    }

    private static final class FakeMetadataStore implements MedisUpdateMetadataStore {
        MedisUpdateMetadata metadata = new MedisUpdateMetadata();

        @NotNull
        @Override
        public MedisUpdateMetadata read() {
            return metadata;
        }

        @Override
        public void markHomepageAccessSuccess(long timestampMillis) {
            metadata = new MedisUpdateMetadata(
                    timestampMillis,
                    metadata.getLastImportSuccessAt(),
                    metadata.getHotVersionDate(),
                    metadata.getSalesVersionDate(),
                    metadata.getHotUrl(),
                    metadata.getSalesUrl()
            );
        }

        @Override
        public void markImportSuccess(@NotNull MedisAutoImportResult result, long timestampMillis) {
            metadata = new MedisUpdateMetadata(
                    metadata.getLastHomepageAccessSuccessAt(),
                    timestampMillis,
                    result.getHotVersionDate(),
                    result.getSalesVersionDate(),
                    result.getHotUrl(),
                    result.getSalesUrl()
            );
        }
    }

    private static final class FakeRemoteDataSource implements MedisRemoteDataSource {
        int discoverCount = 0;
        int downloadCount = 0;
        boolean slowDiscovery = false;

        @Override
        public Object discoverLatestLinks(@NotNull Continuation<? super MedisDownloadLinks> continuation) {
            discoverCount++;
            if (slowDiscovery) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new MedisDownloadLinks(
                    "https://example.test/h20260531_h.zip",
                    "20260531",
                    "https://example.test/A_20260615_2.txt",
                    "20260615"
            );
        }

        @Override
        public Object download(@NotNull String url, @NotNull Continuation<? super byte[]> continuation) {
            downloadCount++;
            return url.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static final class FakeImporter implements MedisMasterImporter {
        int importCount = 0;

        @Override
        public Object importMasters(
                @NotNull MedisDownloadLinks links,
                @NotNull byte[] hotZipBytes,
                @NotNull byte[] salesBytes,
                @NotNull Function1<? super MedisAutoUpdateState.Running, Unit> onProgress,
                @NotNull Continuation<? super MedisAutoImportResult> continuation
        ) {
            importCount++;
            return new MedisAutoImportResult(
                    links.getHotVersionDate(),
                    links.getSalesVersionDate(),
                    links.getHotZipUrl(),
                    links.getSalesFileUrl(),
                    2,
                    3,
                    4
            );
        }
    }
}
