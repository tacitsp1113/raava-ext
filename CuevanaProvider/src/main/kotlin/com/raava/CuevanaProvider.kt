package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CuevanaProvider : MainAPI() {
    override var mainUrl = "https://cuevana3.sc"
    override var name = "Cuevana"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    private val apiUrl = "$mainUrl/wp-json/cuevana/v1"

    override val mainPage = mainPageOf(
        "moviespage" to "Películas",
        "seriespage" to "Series",
        "estrenos" to "Estrenos",
        "tendencias" to "Tendencias",
    )

    private fun String?.toPoster(): String? {
        if (this.isNullOrBlank()) return null
        return if (this.startsWith("http")) this else "https://image.tmdb.org/t/p/w500$this"
    }

    private fun String?.toBackdrop(): String? {
        if (this.isNullOrBlank()) return null
        return if (this.startsWith("http")) this else "https://image.tmdb.org/t/p/original$this"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$apiUrl/${request.data}?paged=$page"
        val json = app.get(url, referer = "$mainUrl/").text
        val response = parseJson<CuevanaCatalogResponse>(json)

        val home = response.data?.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val link = item.link ?: return@mapNotNull null
            val fullLink = fixUrl(link)
            val isMovie = item.info?.type == "movie" || link.contains("/peliculas-")
            val poster = item.info?.cover.toPoster()

            if (isMovie) {
                newMovieSearchResponse(title, fullLink, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, fullLink, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        } ?: emptyList()

        if (home.isEmpty() && page == 1) throw ErrorLoadingException()

        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home),
            hasNext = (page < (response.totalPages ?: 0))
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/search?q=$query"
        val json = app.get(url, referer = "$mainUrl/").text
        val response = parseJson<CuevanaCatalogResponse>(json)

        return response.data?.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val link = item.link ?: return@mapNotNull null
            val fullLink = fixUrl(link)
            val isMovie = item.info?.type == "movie" || link.contains("/peliculas-")
            val poster = item.info?.cover.toPoster()

            if (isMovie) {
                newMovieSearchResponse(title, fullLink, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, fullLink, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = Regex("""/(?:peliculas|series)-online/(\d+)""").find(url)?.groupValues?.getOrNull(1)
            ?: Regex("""/(\d+)""").find(url)?.groupValues?.getOrNull(1)
            ?: return null

        val singleJson = app.get("$apiUrl/single?pid=$id", referer = "$mainUrl/").text
        val singleItem = parseJson<CuevanaSingleResponse>(singleJson).data ?: return null
        val info = singleItem.info

        val title = singleItem.title ?: info?.originalTitle ?: "Sin título"
        val plot = info?.desc?.ifBlank { null }
        val poster = info?.cover.toPoster()
        val backdrop = info?.backdrop.toBackdrop() ?: poster
        val year = info?.release?.split("-")?.firstOrNull()?.toIntOrNull()
        val tags = info?.genres?.mapNotNull { it.firstOrNull()?.toString() }
        val isMovie = info?.type == "movie" || url.contains("/peliculas-")

        return if (isMovie) {
            val movieData = "$apiUrl/player/$id"
            newMovieLoadResponse(title, url, TvType.Movie, movieData) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            val epJson = app.get("$apiUrl/episodes/$id", referer = "$mainUrl/").text
            val epRes = parseJson<CuevanaEpisodesResponse>(epJson)
            val episodes = epRes.data?.flatten()?.map { ep ->
                val sNum = ep.snum ?: 1
                val eNum = ep.enum ?: 1
                val epData = "$apiUrl/episode/$id/$sNum/$eNum"
                newEpisode(epData) {
                    this.name = ep.name
                    this.season = sNum
                    this.episode = eNum
                    this.posterUrl = ep.stillPath.toPoster()
                    this.description = ep.overview
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = app.get(data, referer = "$mainUrl/").text
        val embeds = parseJson<CuevanaPlayerResponse>(json).data?.embeds ?: return false

        embeds.amap { embed ->
            val embedUrl = embed.url ?: return@amap
            val langTag = when (embed.audio?.lowercase()) {
                "latin_spanish", "latino" -> "Latino"
                "spanish", "castellano" -> "Castellano"
                "english", "ingles" -> "Inglés"
                else -> embed.audio ?: "Latino"
            }

            try {
                val fakeDoc = app.get(embedUrl, referer = "$mainUrl/").text
                val realUrl = Regex("""var url = '([^']+)'""").find(fakeDoc)?.groupValues?.getOrNull(1)
                    ?: Regex("""window\.location\.href\s*=\s*'([^']+)'""").find(fakeDoc)?.groupValues?.getOrNull(1)
                    ?: embedUrl

                val fixedUrl = fixHostsLinks(realUrl)
                loadSourceNameExtractor(langTag, fixedUrl, mainUrl, subtitleCallback, callback)
            } catch (e: Exception) {
                logError(e)
            }
        }
        return true
    }

    // Data classes for API JSON serialization
    data class CuevanaItem(
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("info") val info: CuevanaInfo? = null,
    )

    data class CuevanaInfo(
        @JsonProperty("_id") val id: Int? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("cover") val cover: String? = null,
        @JsonProperty("backdrop") val backdrop: String? = null,
        @JsonProperty("desc") val desc: String? = null,
        @JsonProperty("release") val release: String? = null,
        @JsonProperty("ratingValue") val ratingValue: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("runtime") val runtime: Any? = null,
        @JsonProperty("genres") val genres: List<List<Any>>? = null,
    )

    data class CuevanaCatalogResponse(
        @JsonProperty("data") val data: List<CuevanaItem>? = null,
        @JsonProperty("total_pages") val totalPages: Int? = null,
    )

    data class CuevanaSingleResponse(
        @JsonProperty("data") val data: CuevanaItem? = null,
    )

    data class CuevanaEpisode(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("enum") val enum: Int? = null,
        @JsonProperty("snum") val snum: Int? = null,
        @JsonProperty("slug") val slug: String? = null,
    )

    data class CuevanaEpisodesResponse(
        @JsonProperty("data") val data: List<List<CuevanaEpisode>>? = null,
    )

    data class CuevanaEmbed(
        @JsonProperty("server") val server: String? = null,
        @JsonProperty("service_name") val serviceName: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("audio") val audio: String? = null,
    )

    data class CuevanaPlayerData(
        @JsonProperty("embeds") val embeds: List<CuevanaEmbed>? = null,
    )

    data class CuevanaPlayerResponse(
        @JsonProperty("data") val data: CuevanaPlayerData? = null,
    )
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
        .replaceFirst(Regex("""https?://(?:www\.)?(?:mivalyo\.com|dinisglows\.com|dhtpre\.com)"""), "https://vidhidepro.com")
        .replaceFirst("https://filemoon.link", "https://filemoon.sx")
        .replaceFirst("https://sblona.com", "https://watchsb.com")
        .replaceFirst("https://lulu.st", "https://lulustream.com")
        .replaceFirst("https://uqload.io", "https://uqload.com")
        .replaceFirst("https://do7go.com", "https://dood.la")
}
