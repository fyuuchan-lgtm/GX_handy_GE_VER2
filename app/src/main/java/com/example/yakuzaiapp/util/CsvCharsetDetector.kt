package com.example.yakuzaiapp.util

import org.mozilla.universalchardet.UniversalDetector
import java.io.InputStream
import java.nio.charset.Charset

object CsvCharsetDetector {
    fun detectCharset(inputStream: InputStream): Charset {
        val detector = UniversalDetector(null)
        val buffer = ByteArray(4096)
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0 || detector.isDone) break
            detector.handleData(buffer, 0, read)
        }
        detector.dataEnd()
        val detected = detector.detectedCharset ?: return Charset.forName("Shift_JIS")
        return when {
            detected.startsWith("UTF-8", ignoreCase = true) -> Charset.forName("UTF-8")
            detected.contains("SHIFT", ignoreCase = true) -> Charset.forName("Shift_JIS")
            detected.contains("MS932", ignoreCase = true) -> Charset.forName("Shift_JIS")
            else -> runCatching { Charset.forName(detected) }.getOrDefault(Charset.forName("Shift_JIS"))
        }
    }
}
