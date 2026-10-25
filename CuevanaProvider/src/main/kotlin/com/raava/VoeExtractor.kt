package com.raava

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.net.URI

open class VoeExtractor : ExtractorApi() {
    override val name = "VOE"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            val headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to url
            )

            var response = app.get(url, headers = headers)
            var html = response.text

            // Check permanentToken redirect if present
            if (html.contains("permanentToken", ignoreCase = true)) {
                val redirectMatch = Regex("""window\.location\.href\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)
                if (!redirectMatch.isNullOrBlank()) {
                    val nextUrl = if (redirectMatch.startsWith("http")) redirectMatch else URI(url).resolve(redirectMatch).toString()
                    response = app.get(nextUrl, headers = headers)
                    html = response.text
                }
            }

            // Pattern 1: Mathematical VOE decoder
            val arrayMatch = Regex("""json">\s*\[\s*['"]([^'"]+)['"]\s*\]\s*</script>\s*<script[^>]*src=['"]([^'"]+)['"]""").find(html)
            if (arrayMatch != null) {
                val encodedData = arrayMatch.groupValues[1]
                val loaderPath = arrayMatch.groupValues[2]
                val loaderUrl = if (loaderPath.startsWith("http")) loaderPath else URI(url).resolve(loaderPath).toString()

                val loaderCode = app.get(loaderUrl, headers = headers).text
                val patternsMatch = Regex("""(\[(?:'[^']{1,10}'[\s,]*){4,12}\])""").find(loaderCode)
                    ?: Regex("""(\[(?:"[^"]{1,10}"[,\s]*){4,12}\])""").find(loaderCode)

                if (patternsMatch != null) {
                    val decodedJson = voeDecode(encodedData, patternsMatch.groupValues[1])
                    if (decodedJson != null) {
                        val directUrl = decodedJson.optString("source", "").ifEmpty {
                            decodedJson.optString("direct_access_url", "")
                        }
                        if (directUrl.isNotBlank()) {
                            val isM3u8 = directUrl.contains(".m3u8")
                            callback(
                                newExtractorLink(
                                    name,
                                    name,
                                    directUrl,
                                    if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = url
                                    this.quality = Qualities.P1080.value
                                    this.headers = headers
                                }
                            )
                            return
                        }
                    }
                }
            }

            // Pattern 2: Direct mp4 / hls in html
            val directMatch = Regex("""['"](?:mp4|hls)['"]\s*:\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)
                ?: Regex("""file:\s*['"]([^'"]+\.m3u8[^'"]*)['"]""").find(html)?.groupValues?.get(1)

            if (!directMatch.isNullOrBlank()) {
                val isM3u8 = directMatch.contains(".m3u8")
                callback(
                    newExtractorLink(
                        name,
                        name,
                        directMatch,
                        if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = url
                        this.quality = Qualities.P1080.value
                        this.headers = headers
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("VoeExtractor", "Error extracting VOE", e)
        }
    }

    private fun voeDecode(encoded: String, patternsRaw: String): JSONObject? {
        return try {
            val junkPatterns = patternsRaw
                .trim('[', ']')
                .split("','", "\",\"")
                .map { it.trim('\'', '"') }
                .map { Regex.escape(it) }

            // Step 1: Character shift (modified Caesar/ROT13)
            val shifted = StringBuilder()
            for (char in encoded) {
                var u = char.code
                if (u in 65..90) {
                    u = (u - 52) % 26 + 65
                } else if (u in 97..122) {
                    u = (u - 84) % 26 + 97
                }
                shifted.append(u.toChar())
            }

            // Step 2: Remove junk strings
            var cleaned = shifted.toString()
            for (p in junkPatterns) {
                if (p.isNotBlank()) {
                    cleaned = cleaned.replace(Regex(p), "_")
                }
            }
            cleaned = cleaned.replace("_", "")

            // Step 3: Base64 decode 1
            val decoded1 = Base64.decode(cleaned, Base64.DEFAULT)

            // Step 4: Byte offset shift (-3)
            val shiftedBytes = ByteArray(decoded1.size) { i ->
                ((decoded1[i].toInt() - 3 + 256) % 256).toByte()
            }

            // Step 5: Reverse string and Base64 decode 2
            val reversedStr = shiftedBytes.toString(Charsets.ISO_8859_1).reversed()
            val finalJsonBytes = Base64.decode(reversedStr, Base64.DEFAULT)
            val jsonStr = String(finalJsonBytes, Charsets.UTF_8)

            JSONObject(jsonStr)
        } catch (e: Exception) {
            null
        }
    }
}