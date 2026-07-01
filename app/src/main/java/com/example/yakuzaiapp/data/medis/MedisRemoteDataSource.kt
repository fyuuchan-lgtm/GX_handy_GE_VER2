package com.example.yakuzaiapp.data.medis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class MedisDownloadLinks(
    val hotZipUrl: String,
    val hotVersionDate: String,
    val salesFileUrl: String,
    val salesVersionDate: String,
)

interface MedisRemoteDataSource {
    suspend fun discoverLatestLinks(): MedisDownloadLinks
    suspend fun download(url: String): ByteArray
}

class HttpMedisRemoteDataSource : MedisRemoteDataSource {
    override suspend fun discoverLatestLinks(): MedisDownloadLinks = withContext(Dispatchers.IO) {
        val hotHtml = downloadText(HOT_INDEX_URL)
        val salesHtml = downloadText(SALES_INDEX_URL)
        MedisLinkDiscovery.discover(
            hotIndexUrl = HOT_INDEX_URL,
            hotHtml = hotHtml,
            salesIndexUrl = SALES_INDEX_URL,
            salesHtml = salesHtml,
        )
    }

    override suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        openConnection(url).use { connection ->
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                input.copyTo(output)
                output.toByteArray()
            }
        }
    }

    private fun downloadText(url: String): String {
        return openConnection(url).use { connection ->
            connection.inputStream.bufferedReader(charset("Shift_JIS")).use { it.readText() }
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "YakuzaiApp/1.0")
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("HTTP $statusCode: $url")
        }
        return connection
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val HOT_INDEX_URL = "https://www2.medis.or.jp/hcode/"
        const val SALES_INDEX_URL = "https://medhot.medd.jp/view_download"
    }
}

object MedisLinkDiscovery {
    private val hrefRegex = Regex("""(?i)href\s*=\s*["']([^"']+)["']""")
    private val hotRegex = Regex("""h(\d{8})_h\.zip""", RegexOption.IGNORE_CASE)
    private val salesRegex = Regex("""A_(\d{8})_2\.txt""", RegexOption.IGNORE_CASE)

    fun discover(
        hotIndexUrl: String,
        hotHtml: String,
        salesIndexUrl: String,
        salesHtml: String,
    ): MedisDownloadLinks {
        val hot = findLatest(hotIndexUrl, hotHtml, hotRegex)
            ?: throw IllegalStateException("MEDIS HOT のダウンロードリンクが見つかりません。")
        val sales = findLatest(salesIndexUrl, salesHtml, salesRegex)
            ?: throw IllegalStateException("販売名ファイルのダウンロードリンクが見つかりません。")
        return MedisDownloadLinks(
            hotZipUrl = hot.url,
            hotVersionDate = hot.versionDate,
            salesFileUrl = sales.url,
            salesVersionDate = sales.versionDate,
        )
    }

    private fun findLatest(baseUrl: String, html: String, fileRegex: Regex): LinkCandidate? {
        return hrefRegex.findAll(html)
            .mapNotNull { match ->
                val href = match.groupValues[1]
                val fileMatch = fileRegex.find(href) ?: return@mapNotNull null
                LinkCandidate(
                    url = resolve(baseUrl, href),
                    versionDate = fileMatch.groupValues[1],
                )
            }
            .maxByOrNull { it.versionDate }
    }

    private fun resolve(baseUrl: String, href: String): String {
        return URI(baseUrl).resolve(href.replace("&amp;", "&")).toString()
    }

    private data class LinkCandidate(
        val url: String,
        val versionDate: String,
    )
}
