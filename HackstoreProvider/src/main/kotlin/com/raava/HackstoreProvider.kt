package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class HackstoreProvider : MainAPI() {
    override var mainUrl = "https://hackstore2.com"
    override var name = "Hackstore"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    private val apiUrl = "$mainUrl/api/rest"
    private val uploadsUrl = "$mainUrl/wp-content/uploads"

    override val mainPage = mainPageOf(
        "movies" to "Películas",
        "tvshows" to "Series",
        "animes" to "Animes",
    )

    private fun String?.toPoster(): String? {
        if (this.isNullOrBlank()) return null
        return when {
            this.startsWith("http") -> this
            this.startsWith("/") -> "$uploadsUrl$this"
            else -> "$uploadsUrl/$this"
        }
    }

    private fun String?.toBackdrop(): String? {
        if (this.isNullOrBlank()) return null
        return when {
            this.startsWith("http") -> this
            this.startsWith("/") -> "$uploadsUrl$this"
            else -> "$uploadsUrl/$this"
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val postType = request.data
        val url = "$apiUrl/listing?post_type=$postType&page=$page"
        val json = app.get(url, referer = "$mainUrl/").text
        val response = parseJson<HackstoreListingResponse>(json)
        val posts = response.data?.posts ?: emptyList()

        val home = posts.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val slug = item.slug ?: return@mapNotNull null
            val id = item.id ?: return@mapNotNull null
            val type = item.type ?: postType
            val link = "$mainUrl/$type/$slug?id=$id"
            val poster = item.images?.poster.toPoster()

            val tvType = when (type) {
                "movies" -> TvType.Movie
                "animes" -> TvType.Anime
                else -> TvType.TvSeries
            }

            if (tvType == TvType.Movie) {
                newMovieSearchResponse(title, link, tvType) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, link, tvType) {
                    this.posterUrl = poster
                }
            }
        }

        if (home.isEmpty() && page == 1) throw ErrorLoadingException()

        val lastPage = response.data?.pagination?.lastPage ?: 1
        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home),
            hasNext = page < lastPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val types = listOf("movies", "tvshows", "animes")
        return types.amap { type ->
            val url = "$apiUrl/search?post_type=$type&query=$query"
            try {
                val json = app.get(url, referer = "$mainUrl/").text
                val response = parseJson<HackstoreSearchResponse>(json)
                response.data?.posts?.mapNotNull { item ->
                    val title = item.title ?: return@mapNotNull null
                    val slug = item.slug ?: return@mapNotNull null
                    val id = item.id ?: return@mapNotNull null
                    val itemType = item.type ?: type
                    val link = "$mainUrl/$itemType/$slug?id=$id"
                    val poster = item.images?.poster.toPoster()

                    val tvType = when (itemType) {
                        "movies" -> TvType.Movie
                        "animes" -> TvType.Anime
                        else -> TvType.TvSeries
                    }

                    if (tvType == TvType.Movie) {
                        newMovieSearchResponse(title, link, tvType) {
                            this.posterUrl = poster
                        }
                    } else {
                        newTvSeriesSearchResponse(title, link, tvType) {
                            this.posterUrl = poster
                        }
                    }
                } ?: emptyList()
            } catch (e: Throwable) {
                emptyList()
            }
        }.flatten()
    }

    override suspend fun load(url: String): LoadResponse? {
        val idFromUrl = Regex("""[?&]id=(\d+)""").find(url)?.groupValues?.getOrNull(1)

        val slug = url.substringBefore("?").split("/").lastOrNull { it.isNotBlank() } ?: return null
        val typeSegment = url.split("/").getOrNull(3) ?: "movies"
        val postType = when (typeSegment) {
            "series", "tvshows" -> "tvshows"
            "animes" -> "animes"
            else -> "movies"
        }

        val singleJson = app.get("$apiUrl/single?post_name=$slug&post_type=$postType", referer = "$mainUrl/").text
        val singleResponse = parseJson<HackstoreSingleResponse>(singleJson)
        val item = singleResponse.data ?: return null

        val id = item.id?.toString() ?: idFromUrl ?: return null
        val title = item.title ?: item.originalTitle ?: "Sin título"
        val plot = item.overview?.ifBlank { null }
        val poster = item.images?.poster.toPoster()
        val backdrop = item.images?.backdrop.toBackdrop() ?: poster
        val year = item.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val isMovie = (item.type ?: postType) == "movies"

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, id) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = plot
                this.year = year
            }
        } else {
            val tvType = if ((item.type ?: postType) == "animes") TvType.Anime else TvType.TvSeries
            val epJson = app.get("$apiUrl/episodes?post_id=$id", referer = "$mainUrl/").text
            val epResponse = parseJson<HackstoreEpisodesResponse>(epJson)
            val episodes = epResponse.data?.mapNotNull { ep ->
                val epId = ep.id?.toString() ?: return@mapNotNull null
                val sNum = ep.seasonNumber ?: 1
                val eNum = ep.episodeNumber ?: 1
                val epStill = if (!ep.stillPath.isNullOrBlank()) {
                    if (ep.stillPath.startsWith("http")) ep.stillPath else "https://image.tmdb.org/t/p/w500${ep.stillPath}"
                } else null

                newEpisode(epId) {
                    this.name = ep.title
                    this.season = sNum
                    this.episode = eNum
                    this.posterUrl = epStill
                    this.description = ep.overview
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val postId = data.trim()
        val playerJson = app.get("$apiUrl/player?post_id=$postId", referer = "$mainUrl/").text
        val playerResponse = parseJson<HackstorePlayerResponse>(playerJson)

        playerResponse.data?.forEach { item ->
            val link = item.url ?: return@forEach
            val cleanUrl = fixUrl(link)

            when {
                cleanUrl.contains("vimeos.") || cleanUrl.contains("goodstream.") -> {
                    resolvePackedStream(cleanUrl, subtitleCallback, callback)
                }
                else -> {
                    loadExtractor(cleanUrl, mainUrl, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    private suspend fun resolvePackedStream(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val html = app.get(url, referer = "$mainUrl/").text
            val unpacked = getAndUnpack(html)

            val m3u8 = Regex("""sources:\s*\[\{file:\s*["']([^"']+)["']""").find(unpacked)?.groupValues?.getOrNull(1)
                ?: Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)?.groupValues?.getOrNull(1)

            if (!m3u8.isNullOrBlank()) {
                val hostName = Regex("""https?://([^/]+)""").find(url)?.groupValues?.getOrNull(1) ?: "Stream"
                callback.invoke(
                    newExtractorLink(
                        hostName,
                        hostName,
                        m3u8
                    ) {
                        this.referer = url
                        this.type = ExtractorLinkType.M3U8
                    }
                )
            }

            Regex("""file:\s*["']([^"']+\.vtt)["'],\s*label:\s*["']([^"']+)["']""").findAll(unpacked).forEach { match ->
                val subUrl = match.groupValues.getOrNull(1) ?: return@forEach
                val subLang = match.groupValues.getOrNull(2) ?: "Sub"
                subtitleCallback.invoke(
                    SubtitleFile(subLang, fixUrl(subUrl))
                )
            }
        } catch (_: Throwable) {
        }
    }

    // --- Data Classes ---

    data class HackstoreListingResponse(
        @JsonProperty("error") val error: Boolean? = null,
        @JsonProperty("data") val data: HackstoreListingData? = null
    )

    data class HackstoreListingData(
        @JsonProperty("posts") val posts: List<HackstorePost>? = null,
        @JsonProperty("pagination") val pagination: HackstorePagination? = null
    )

    data class HackstoreSearchResponse(
        @JsonProperty("error") val error: Boolean? = null,
        @JsonProperty("data") val data: HackstoreSearchData? = null
    )

    data class HackstoreSearchData(
        @JsonProperty("posts") val posts: List<HackstorePost>? = null
    )

    data class HackstoreSingleResponse(
        @JsonProperty("error") val error: Boolean? = null,
        @JsonProperty("data") val data: HackstorePost? = null
    )

    data class HackstorePost(
        @JsonProperty("_id") val id: Long? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("images") val images: HackstoreImages? = null,
        @JsonProperty("rating") val rating: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("runtime") val runtime: String? = null,
        @JsonProperty("tagline") val tagline: String? = null
    )

    data class HackstoreImages(
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("backdrop") val backdrop: String? = null,
        @JsonProperty("logo") val logo: String? = null
    )

    data class HackstorePagination(
        @JsonProperty("current_page") val currentPage: Int? = null,
        @JsonProperty("last_page") val lastPage: Int? = null,
        @JsonProperty("total") val total: Int? = null
    )

    data class HackstoreEpisodesResponse(
        @JsonProperty("error") val error: Boolean? = null,
        @JsonProperty("data") val data: List<HackstoreEpisode>? = null
    )

    data class HackstoreEpisode(
        @JsonProperty("_id") val id: Long? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null
    )

    data class HackstorePlayerResponse(
        @JsonProperty("error") val error: Boolean? = null,
        @JsonProperty("data") val data: List<HackstorePlayerItem>? = null
    )

    data class HackstorePlayerItem(
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("url") val url: String? = null
    )
}
