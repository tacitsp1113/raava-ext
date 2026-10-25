package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class TacitCloudProvider : MainAPI() {
    override var mainUrl = "https://raava-str-1.onrender.com"
    override var name = "TACIT Cloud"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "movie" to "Películas",
        "series" to "Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val type = request.data
        val url = "$mainUrl/api/cs/home?type=$type&page=$page&limit=30"
        val json = app.get(url).text
        val response = parseJson<CsHomeResponse>(json)

        val items = response.results.map { item ->
            val isMovie = item.type == "movie"
            val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
            val dataUrl = "$mainUrl/api/cs/load?type=${item.type}&id=${item.id}"

            if (isMovie) {
                newMovieSearchResponse(item.title, dataUrl, tvType) {
                    this.posterUrl = item.poster
                    this.year = item.year?.toIntOrNull()
                }
            } else {
                newTvSeriesSearchResponse(item.title, dataUrl, tvType) {
                    this.posterUrl = item.poster
                    this.year = item.year?.toIntOrNull()
                }
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = response.hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/cs/search?q=${query.trim()}"
        val json = app.get(url).text
        val results = parseJson<List<CsSearchItem>>(json)

        return results.map { item ->
            val isMovie = item.type == "movie"
            val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
            val dataUrl = "$mainUrl/api/cs/load?type=${item.type}&id=${item.id}"

            if (isMovie) {
                newMovieSearchResponse(item.title, dataUrl, tvType) {
                    this.posterUrl = item.poster
                    this.year = item.year?.toIntOrNull()
                }
            } else {
                newTvSeriesSearchResponse(item.title, dataUrl, tvType) {
                    this.posterUrl = item.poster
                    this.year = item.year?.toIntOrNull()
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val json = app.get(url).text
        val details = parseJson<CsLoadDetails>(json)
        val isMovie = details.type == "movie"
        val tvType = if (isMovie) TvType.Movie else TvType.TvSeries

        return if (isMovie) {
            val linkData = "$mainUrl/api/cs/links?type=movie&id=${details.id}"
            newMovieLoadResponse(details.title, url, tvType, linkData) {
                this.posterUrl = details.poster
                this.year = details.year?.toIntOrNull()
                this.plot = details.genres
            }
        } else {
            val episodes = details.episodes.map { ep ->
                val linkData = "$mainUrl/api/cs/links?type=series&id=${details.id}&season=${ep.season}&episode=${ep.episode}"
                newEpisode(linkData) {
                    this.name = ep.name
                    this.season = ep.season
                    this.episode = ep.episode
                }
            }

            newTvSeriesLoadResponse(details.title, url, tvType, episodes) {
                this.posterUrl = details.poster
                this.year = details.year?.toIntOrNull()
                this.plot = details.genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = app.get(data).text
        val res = parseJson<CsLinksResponse>(json)

        res.streams.forEach { stream ->
            val qualInt = when {
                stream.quality.contains("4k", ignoreCase = true) -> 2160
                stream.quality.contains("1080", ignoreCase = true) -> 1080
                stream.quality.contains("720", ignoreCase = true) -> 720
                else -> 1080
            }

            val sizeLabel = if (!stream.size.isNullOrBlank()) " (${stream.size})" else ""

            callback.invoke(
                newExtractorLink(
                    source = "TACIT Cloud",
                    name = "TACIT Cloud ${stream.quality}$sizeLabel",
                    url = stream.url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = qualInt
                }
            )
        }

        return res.streams.isNotEmpty()
    }
}

data class CsHomeResponse(
    val page: Int,
    @JsonProperty("has_next") val hasNext: Boolean = false,
    val results: List<CsSearchItem> = emptyList()
)

data class CsSearchItem(
    val id: Long,
    val title: String,
    val year: String? = null,
    val poster: String? = null,
    val vote: Double? = null,
    val type: String
)

data class CsLoadDetails(
    val id: Long,
    val title: String,
    val year: String? = null,
    val poster: String? = null,
    val vote: Double? = null,
    val genres: String? = null,
    val type: String,
    val episodes: List<CsEpisodeItem> = emptyList()
)

data class CsEpisodeItem(
    val name: String,
    val season: Int,
    val episode: Int,
    val size: String? = null,
    val data: String
)

data class CsLinksResponse(
    val streams: List<CsStreamItem> = emptyList()
)

data class CsStreamItem(
    val name: String,
    val url: String,
    val quality: String,
    val size: String? = null
)
