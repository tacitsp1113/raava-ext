package com.lagradost.cloudstream3.movieproviders

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.jsoup.Jsoup
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PelisPlusProvider : MainAPI() {
    override var mainUrl = "https://pelisplushd.bz"
    override var name = "PelisPlus"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "peliculas" to "Películas",
        "series" to "Series",
        "estrenos" to "Estrenos",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) "$mainUrl/${request.data}" else "$mainUrl/${request.data}?page=$page"
        val doc = app.get(url, referer = "$mainUrl/").document

        val home = doc.select("a.Posters-link").mapNotNull { a ->
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"
            val rawTitle = a.attr("data-title").ifBlank { a.selectFirst("img")?.attr("alt") ?: "" }
            val title = cleanTitle(rawTitle)
            val poster = a.selectFirst("img")?.attr("src")?.takeIf { it.startsWith("http") }
                ?: a.selectFirst("img")?.attr("data-src")

            val isMovie = a.hasClass("movies") || href.contains("/pelicula/")

            if (isMovie) {
                newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        }

        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home),
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        val doc = app.get(url, referer = "$mainUrl/").document

        return doc.select("a.Posters-link").mapNotNull { a ->
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"
            val rawTitle = a.attr("data-title").ifBlank { a.selectFirst("img")?.attr("alt") ?: "" }
            val title = cleanTitle(rawTitle)
            val poster = a.selectFirst("img")?.attr("src")?.takeIf { it.startsWith("http") }
                ?: a.selectFirst("img")?.attr("data-src")

            val isMovie = a.hasClass("movies") || href.contains("/pelicula/")

            if (isMovie) {
                newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, referer = "$mainUrl/").document
        val isMovie = url.contains("/pelicula/")

        val rawTitle = doc.selectFirst("h1, .title, .title_seo")?.text().orEmpty()
        val title = cleanTitle(rawTitle).ifBlank { "Sin título" }
        val poster = doc.selectFirst(".Posters-img, .poster img, meta[property='og:image']")?.attr("src")
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")
        val plot = doc.selectFirst(".read-more-target, .read-more-wrap p, .info-content, .text")?.text()
        val yearMatch = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = yearMatch
            }
        } else {
            val episodes = doc.select("a[href*='/temporada/'][href*='/capitulo/']").mapNotNull { a ->
                val epHref = a.attr("href").ifBlank { return@mapNotNull null }
                val fullEpHref = if (epHref.startsWith("http")) epHref else "$mainUrl$epHref"
                val match = Regex("""/temporada/(\d+)/capitulo/(\d+)""").find(epHref)
                val sNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val eNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1

                newEpisode(fullEpHref) {
                    this.name = "Temporada $sNum Episodio $eNum"
                    this.season = sNum
                    this.episode = eNum
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = yearMatch
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data, referer = "$mainUrl/").text
        var foundAny = false

        // Pattern 1: video[id] = 'url'
        val matches = Regex("""video\[\d+\]\s*=\s*['"]([^'"]+)['"]""").findAll(html).toList()
        for (m in matches) {
            val embedUrl = m.groupValues[1]
            if (embedUrl.contains("embed69.org")) {
                val decryptedLinks = solveEmbed69(embedUrl)
                for (pair in decryptedLinks) {
                    foundAny = true
                    val fixed = fixHostsLinks(pair.first)
                    loadSourceNameExtractor(pair.second, fixed, embedUrl, subtitleCallback, callback)
                }
            } else {
                foundAny = true
                val fixed = fixHostsLinks(embedUrl)
                loadSourceNameExtractor("Latino", fixed, data, subtitleCallback, callback)
            }
        }

        // Pattern 2: iframe in html
        val doc = Jsoup.parse(html)
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank() && !src.contains("youtube") && !src.contains("google")) {
                if (src.contains("embed69.org")) {
                    val decryptedLinks = solveEmbed69(src)
                    for (pair in decryptedLinks) {
                        foundAny = true
                        val fixed = fixHostsLinks(pair.first)
                        loadSourceNameExtractor(pair.second, fixed, src, subtitleCallback, callback)
                    }
                } else {
                    foundAny = true
                    val fixed = fixHostsLinks(src)
                    loadSourceNameExtractor("Latino", fixed, data, subtitleCallback, callback)
                }
            }
        }

        return foundAny
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("""^VER\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(\d{4}\)?.*$"""), "")
            .replace(Regex("""\s+Online\s+Gratis\s+HD$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private suspend fun solveEmbed69(embedUrl: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val html = app.get(embedUrl, headers = mapOf("Referer" to "https://sololatino.net/")).text
            val dataLinkJson = Regex("""dataLink\s*=\s*(\[[\s\S]*?\]);""").find(html)?.groupValues?.get(1) ?: return list

            val challenge = Regex("""POW_CHALLENGE\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)
            val salt = Regex("""POW_SALT\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)
            val difficulty = Regex("""POW_DIFFICULTY\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 3

            var keyBytes: ByteArray? = null
            if (!challenge.isNullOrBlank() && !salt.isNullOrBlank()) {
                val md = MessageDigest.getInstance("SHA-256")
                for (n in 0..2000000) {
                    val input = (challenge + n).toByteArray(Charsets.UTF_8)
                    val hash = md.digest(input)
                    var ok = true
                    for (i in 0 until difficulty) {
                        val nibble = if (i % 2 == 0) {
                            (hash[i / 2].toInt() shr 4) and 0x0F
                        } else {
                            hash[i / 2].toInt() and 0x0F
                        }
                        if (nibble != 0) {
                            ok = false
                            break
                        }
                    }
                    if (ok) {
                        val keyInput = (challenge + n + salt).toByteArray(Charsets.UTF_8)
                        keyBytes = MessageDigest.getInstance("SHA-256").digest(keyInput)
                        break
                    }
                }
            }

            val groups = JSONArray(dataLinkJson)
            for (gi in 0 until groups.length()) {
                val group = groups.getJSONObject(gi)
                val langCode = group.optString("video_language", "LAT").uppercase()
                val audioTag = when (langCode) {
                    "LAT" -> "Latino"
                    "CAS", "ESP" -> "Castellano"
                    "SUB" -> "Subtitulado"
                    else -> langCode
                }
                val embeds = group.optJSONArray("sortedEmbeds") ?: continue
                for (ei in 0 until embeds.length()) {
                    val embed = embeds.getJSONObject(ei)
                    val cipherLink = embed.optString("link", "")
                    if (cipherLink.isBlank() || embed.optString("servername") == "download") continue

                    var realUrl = cipherLink
                    if (keyBytes != null) {
                        try {
                            val raw = Base64.decode(cipherLink, Base64.DEFAULT)
                            if (raw.size > 16) {
                                val iv = ByteArray(16) { raw[it] }
                                val ciphertext = ByteArray(raw.size - 16) { raw[it + 16] }
                                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                                realUrl = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
                            }
                        } catch (_: Exception) {}
                    }
                    if (realUrl.startsWith("http")) {
                        list.add(Pair(realUrl, audioTag))
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }
}

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "$source [${link.name}]",
                    "$source [${link.name}]",
                    link.url,
                ) {
                    this.quality = link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}

fun fixHostsLinks(url: String): String {
    return url
        .replaceFirst(Regex("""https?://(?:www\.)?(?:repacklab\.com|fraudulentwelfare\.com|yodabox\.net|launchprivilege\.com|simpelt\.com|metatube\.cc|bigwarp\.io|audioclip\.net|playvoe\.com|voe-unblock\.com)"""), "https://voe.sx")
        .replaceFirst(Regex("""https?://(?:www\.)?(?:hglink\.to|swdyu\.com|cybervynx\.com|dumbalag\.com)"""), "https://streamwish.to")
        .replaceFirst(Regex("""https?://(?:www\.)?(?:mivalyo\.com|dinisglows\.com|dhtpre\.com|morencius\.com)"""), "https://vidhidepro.com")
        .replaceFirst("https://filemoon.link", "https://filemoon.sx")
        .replaceFirst("https://sblona.com", "https://watchsb.com")
        .replaceFirst("https://lulu.st", "https://lulustream.com")
        .replaceFirst("https://uqload.io", "https://uqload.com")
        .replaceFirst("https://do7go.com", "https://dood.la")
}