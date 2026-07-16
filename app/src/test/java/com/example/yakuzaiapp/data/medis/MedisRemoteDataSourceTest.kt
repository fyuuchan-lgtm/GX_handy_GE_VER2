package com.example.yakuzaiapp.data.medis

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class MedisRemoteDataSourceTest {
    @Test
    fun downloadRejectsPlainHttpBeforeConnecting() = runBlocking {
        val error = runCatching {
            HttpMedisRemoteDataSource().download("http://example.com/medis.zip")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun redirectRejectsDowngradeToHttp() {
        val error = runCatching {
            resolveMedisRedirect(
                currentUri = URI("https://www2.medis.or.jp/hcode/"),
                location = "http://example.com/medis.zip",
                redirectCount = 1,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun redirectRejectsMoreThanFiveHops() {
        val error = runCatching {
            resolveMedisRedirect(
                currentUri = URI("https://www2.medis.or.jp/hcode/"),
                location = "https://example.com/medis.zip",
                redirectCount = 6,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
