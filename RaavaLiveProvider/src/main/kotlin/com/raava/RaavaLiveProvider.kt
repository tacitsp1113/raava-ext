package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class RaavaLiveProvider : MainAPI() {
    override var mainUrl = "https://streamtp99a.sbs"
    override var name = "RAAVA LIVE"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    override val vpnStatus = VPNStatus.None

    private val mirrors = listOf(
        "https://streamtp99a.sbs",
        "https://streamtp-golden1.click"
    )

    private val streamBase = "https://streamtp99a.sbs"

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val headers = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "$mainUrl/"
    )

    data class StreamTpEvent(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("time") val time: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("url") val url: String? = null
    )

    data class LiveChannel(
        val id: String,
        val name: String,
        val stream: String,
        val logo: String
    )

    private val fixedChannels = listOf(
        LiveChannel("ch_espn", "ESPN Latino", "espn", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/espn-us.png"),
        LiveChannel("ch_espnmx", "ESPN México", "espnmx", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/espn-us.png"),
        LiveChannel("ch_espn2", "ESPN 2", "espn2", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/espn-2-us.png"),
        LiveChannel("ch_espn3", "ESPN 3", "espn3", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/espn-3-us.png"),
        LiveChannel("ch_espn4", "ESPN 4", "espn4", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/espn-us.png"),
        LiveChannel("ch_espndeportes", "ESPN Deportes", "espndeportes", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-states/espn-us.png"),
        LiveChannel("ch_tudn", "TUDN México", "tudnmx", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/mexico/tudn-mx.png"),
        LiveChannel("ch_tudnus", "TUDN USA", "tudnus", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/mexico/tudn-mx.png"),
        LiveChannel("ch_azteca7", "Azteca 7 (Deportes)", "azteca7", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/mexico/azteca-7-mx.png"),
        LiveChannel("ch_foxsports", "Fox Sports", "fox1ar", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/fox-sports-ar.png"),
        LiveChannel("ch_disney1", "Disney+ / Star+ 1", "disney1", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80"),
        LiveChannel("ch_disney2", "Disney+ / Star+ 2", "disney2", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80"),
        LiveChannel("ch_disney3", "Disney+ / Star+ 3", "disney3", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80"),
        LiveChannel("ch_max1", "TNT Sports / Max 1", "max1", "https://images.unsplash.com/photo-1489944440615-453fc2b6a9a9?auto=format&fit=crop&w=600&q=80"),
        LiveChannel("ch_max2", "TNT Sports / Max 2", "max2", "https://images.unsplash.com/photo-1489944440615-453fc2b6a9a9?auto=format&fit=crop&w=600&q=80"),
        LiveChannel("ch_winplus", "Win Sports+ (Colombia)", "winplus", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=600&q=80")
    )

    override val mainPage = mainPageOf(
        "agenda" to "⚽ Partidos y Eventos de Hoy",
        "channels" to "📺 Canales Deportivos 24/7",
        "motor" to "🏎️ Motor (F1, Rally, WRC)",
        "combat" to "🥊 Combate (UFC, Boxeo)",
        "football" to "⚽ Fútbol Internacional"
    )

    private fun getPosterForEvent(category: String?, title: String?): String {
        val c = (category ?: "").lowercase()
        val t = (title ?: "").lowercase()
        return when {
            c.contains("f1") || c.contains("motor") || t.contains("formula") || t.contains("rally") || t.contains("wrc") ->
                "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=600&q=80"
            c.contains("basket") || c.contains("nba") || t.contains("nba") ->
                "https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=600&q=80"
            c.contains("tennis") || t.contains("atp") || t.contains("wta") ->
                "https://images.unsplash.com/photo-1595435934249-5df7ed86e1c0?auto=format&fit=crop&w=600&q=80"
            c.contains("fight") || c.contains("ufc") || c.contains("box") ->
                "https://images.unsplash.com/photo-1517438322307-e67111335449?auto=format&fit=crop&w=600&q=80"
            else ->
                "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=600&q=80"
        }
    }

    private suspend fun fetchLiveEvents(): List<StreamTpEvent> {
        for (mirror in mirrors) {
            try {
                val json = app.get(
                    "$mirror/events.json",
                    headers = mapOf("Referer" to "$mirror/", "User-Agent" to userAgent),
                    timeout = 6
                ).text
                if (json.isNotBlank() && json.startsWith("[")) {
                    return parseJson<List<StreamTpEvent>>(json)
                }
            } catch (_: Exception) {
                // Try next mirror
            }
        }
        return emptyList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryKey = request.data
        val items = mutableListOf<SearchResponse>()

        if (categoryKey == "channels") {
            fixedChannels.forEach { ch ->
                val searchItem = newLiveSearchResponse(
                    name = ch.name,
                    url = "raava_ch:${ch.stream}",
                    type = TvType.Live
                ) {
                    this.posterUrl = ch.logo
                    this.posterHeaders = headers
                }
                items.add(searchItem)
            }
        } else {
            val events = fetchLiveEvents()
            val filtered = when (categoryKey) {
                "motor" -> events.filter {
                    val c = (it.category ?: "").lowercase()
                    val t = (it.title ?: "").lowercase()
                    c.contains("f1") || c.contains("motor") || t.contains("formula") || t.contains("rally")
                }
                "combat" -> events.filter {
                    val c = (it.category ?: "").lowercase()
                    c.contains("fight") || c.contains("ufc") || c.contains("box")
                }
                "football" -> events.filter {
                    val c = (it.category ?: "").lowercase()
                    c.contains("futbol") || c.contains("soccer") || c.contains("football")
                }
                else -> events // "agenda" (todos los eventos)
            }

            filtered.forEach { ev ->
                val title = ev.title ?: return@forEach
                val timePrefix = if (!ev.time.isNullOrBlank()) "[${ev.time}] " else ""
                val qualityTag = if (!ev.quality.isNullOrBlank()) " (${ev.quality})" else ""
                val displayName = "$timePrefix$title$qualityTag"
                val targetUrl = ev.url ?: return@forEach
                val poster = getPosterForEvent(ev.category, ev.title)

                val searchItem = newLiveSearchResponse(
                    name = displayName,
                    url = targetUrl,
                    type = TvType.Live
                ) {
                    this.posterUrl = poster
                    this.posterHeaders = headers
                }
                items.add(searchItem)
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = true // 👈 Tarjetas panorámicas 16:9 estilo deportivo
            ),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        val results = mutableListOf<SearchResponse>()

        // 1. Canales fijos que coincidan
        fixedChannels.filter { it.name.lowercase().contains(q) }.forEach { ch ->
            results.add(
                newLiveSearchResponse(ch.name, "raava_ch:${ch.stream}", TvType.Live) {
                    this.posterUrl = ch.logo
                    this.posterHeaders = headers
                }
            )
        }

        // 2. Eventos de la agenda que coincidan
        val events = fetchLiveEvents()
        events.filter {
            (it.title ?: "").lowercase().contains(q) || (it.category ?: "").lowercase().contains(q)
        }.forEach { ev ->
            val title = ev.title ?: return@forEach
            val timePrefix = if (!ev.time.isNullOrBlank()) "[${ev.time}] " else ""
            val targetUrl = ev.url ?: return@forEach
            results.add(
                newLiveSearchResponse("$timePrefix$title", targetUrl, TvType.Live) {
                    this.posterUrl = getPosterForEvent(ev.category, ev.title)
                    this.posterHeaders = headers
                }
            )
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val isFixedChannel = url.startsWith("raava_ch:")
        val title: String
        val poster: String
        val plot: String

        if (isFixedChannel) {
            val streamKey = url.removePrefix("raava_ch:")
            val ch = fixedChannels.find { it.stream == streamKey }
            title = ch?.name ?: "Canal en Directo"
            poster = ch?.logo ?: "https://raw.githubusercontent.com/tacitsp1113/raava-str/main/logo.png"
            plot = "Transmisión oficial en vivo 24/7 • $title"
        } else {
            val events = fetchLiveEvents()
            val ev = events.find { it.url == url }
            title = ev?.title ?: "Evento en Vivo"
            poster = getPosterForEvent(ev?.category, ev?.title)
            plot = "Categoría: ${ev?.category ?: "Deportes"}\nHora: ${ev?.time ?: "En vivo"}\nCalidad: ${ev?.quality ?: "HD"}"
        }

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Live,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    private suspend fun resolveStreamUrl(url: String): String? {
        var currentUrl = url
        for (redirectCount in 0..2) {
            val html = try {
                app.get(
                    currentUrl,
                    headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "$streamBase/"
                    ),
                    timeout = 15
                ).text
            } catch (_: Exception) {
                return null
            }

            val match = Regex("""var\s+playbackURL\s*=\s*["']([^"']+)["']""").find(html)
            val streamUrl = match?.groups?.get(1)?.value?.replace("\\/", "/")
            if (!streamUrl.isNullOrBlank()) {
                return streamUrl
            }

            // Check if there is a JS redirect: const nuevoDominio = "https://..."
            val jsDomainMatch = Regex("""(?:const|var|let)\s+nuevoDominio\s*=\s*["']([^"']+)["']""").find(html)
            if (jsDomainMatch != null) {
                val nuevoDominio = jsDomainMatch.groups[1]?.value?.trimEnd('/') ?: break
                val path = currentUrl.substringAfter("://").substringAfter("/", "")
                currentUrl = "$nuevoDominio/$path"
                continue
            }

            val jsRedirectMatch = Regex("""window\.location\.replace\(["']([^"']+)["']\)""").find(html)
            if (jsRedirectMatch != null) {
                currentUrl = jsRedirectMatch.groups[1]?.value ?: break
                continue
            }

            break
        }
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val targetUrl = when {
            data.startsWith("raava_ch:") -> {
                val streamKey = data.removePrefix("raava_ch:")
                "$streamBase/global1.php?stream=$streamKey"
            }
            data.startsWith("http") -> data
            else -> "$streamBase/global1.php?stream=$data"
        }

        val streamUrl = resolveStreamUrl(targetUrl)
            ?: run {
                val streamKey = targetUrl.substringAfter("stream=", "")
                if (streamKey.isNotBlank()) {
                    resolveStreamUrl("https://streamtp99a.sbs/global1.php?stream=$streamKey")
                } else null
            }

        if (!streamUrl.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "Señal En Vivo HD",
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$streamBase/"
                    this.quality = Qualities.P1080.value
                    this.headers = mapOf(
                        "Referer" to "$streamBase/",
                        "Origin" to streamBase,
                        "User-Agent" to userAgent
                    )
                }
            )
            return true
        }

        return false
    }
}
