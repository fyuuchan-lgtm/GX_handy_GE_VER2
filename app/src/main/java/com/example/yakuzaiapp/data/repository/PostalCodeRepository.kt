package com.example.yakuzaiapp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class PostalAddress(
    val postalCode: String,
    val prefecture: String,
    val city: String,
    val town: String
)

const val POSTAL_ERROR_INVALID_POSTAL_CODE = "INVALID_POSTAL_CODE"
const val POSTAL_ERROR_SEARCH_FAILED = "POSTAL_SEARCH_FAILED"

class PostalCodeRepository {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(postalCode: String): Result<List<PostalAddress>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedPostalCode = postalCode.filter { it.isDigit() }
            require(normalizedPostalCode.length == 7) {
                POSTAL_ERROR_INVALID_POSTAL_CODE
            }
            val connection = URL("$ENDPOINT?zipcode=$normalizedPostalCode").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.use {
                val body = if (it.responseCode in 200..299) {
                    it.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
                } else {
                    it.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
                }
                val response = json.decodeFromString(ZipCloudResponse.serializer(), body)
                if (response.status != 200) {
                    error(POSTAL_ERROR_SEARCH_FAILED)
                }
                response.results.orEmpty().map { result ->
                    PostalAddress(
                        postalCode = result.zipcode,
                        prefecture = result.address1,
                        city = result.address2,
                        town = result.address3
                    )
                }
            }
        }
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://zipcloud.ibsnet.co.jp/api/search"
    }
}

@Serializable
private data class ZipCloudResponse(
    val status: Int,
    val message: String? = null,
    val results: List<ZipCloudAddress>? = null
)

@Serializable
private data class ZipCloudAddress(
    val zipcode: String,
    @SerialName("address1") val address1: String,
    @SerialName("address2") val address2: String,
    @SerialName("address3") val address3: String
)
