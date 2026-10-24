package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class CinecalidadProvider : MainAPI() {
    override var mainUrl = "https://www.cinecalidad.am"
    override var name = "Cinecalidad"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "ver-serie" to "Series",
        "fecha-de-lanzamiento/2026" to "Estrenos 2026",
        "fecha-de-lanzamiento/2025" to "Estrenos 2025",
        "genero-de-la-pelicula/accion" to "Acción",
        "genero-de-la-pelicula/animacion" to "Animación",
        "genero-de-la-pelicula/comedia" to "Comedia",
        "genero-de-la-pelicula/terror" to "Terror",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page <= 1) {
            "$mainUrl/${request.data}/"
        } else {
            "$mainUrl/${request.data}/page/$page/"
        }
        val document = app.get(pageUrl).document
        val home = document.select(".item.movies")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.in_title")?.text()?.trim()
            ?: this.selectFirst(".entry-title, h2, h3")?.text()?.trim()
            ?: return null

        val link = this.selectFirst("a")?.attr("href") ?: return null

        val posterUrl = this.selectFirst(".poster.custom img, img")?.let { img ->
            val dataSrc = img.attr("data-src").takeIf { it.isNotBlank() }
            val src = img.attr("src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
            dataSrc ?: src
        }

        val isMovie = !link.contains("/ver-serie/")
        val type = if (isMovie) TvType.Movie else TvType.TvSeries

        return if (isMovie) {
            newMovieSearchResponse(title, link, type) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, link, type) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=${query}").document
        return document.select("article.item, .item.movies").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document

        val title = soup.selectFirst(".single_left h1")?.text()?.trim()
            ?: soup.selectFirst("h1.entry-title, h1")?.text()?.trim()
            ?: return null

        val description = soup.selectFirst("div.single_left table tbody tr td p")?.text()?.trim()
            ?: soup.selectFirst(".entry-content p, #sinopsis, .sinopsis")?.text()?.trim()

        val poster: String? = soup.selectFirst(".single_left img.alignnone, img.alignnone")?.let { img ->
            val dataSrc = img.attr("data-src").takeIf { it.isNotBlank() }
            val src = img.attr("src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
            dataSrc ?: src
        } ?: soup.selectFirst(".poster img")?.attr("data-src")

        val episodes = soup.select("div.se-c div.se-a ul.episodios li").mapNotNull { li ->
            val href = li.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val epThumb = li.selectFirst("img.lazy, img")?.let { img ->
                val dataSrc = img.attr("data-src").takeIf { it.isNotBlank() }
                val src = img.attr("src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
                dataSrc ?: src
            }
            val epName = li.selectFirst(".episodiotitle a")?.text()?.trim() ?: "Episodio"
            val seasonid = li.selectFirst(".numerando")?.text()?.replace(Regex("(S|E)"), "")?.let { str ->
                str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val isValid = seasonid != null && seasonid.size == 2
            val episodeNum = if (isValid) seasonid?.getOrNull(1) else null
            val seasonNum = if (isValid) seasonid?.getOrNull(0) else null

            newEpisode(href) {
                this.name = epName
                this.season = seasonNum
                this.episode = episodeNum
                this.posterUrl = epThumb
            }
        }

        val isMovie = url.contains("/ver-pelicula/") || episodes.isEmpty()
        val tvType = if (isMovie) TvType.Movie else TvType.TvSeries

        return if (!isMovie) {
            newTvSeriesLoadResponse(
                title,
                url,
                tvType,
                episodes,
            ) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                tvType,
                url
            ) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val optionElements = document.select(".linklist ul li[data-option], ul#playeroptionsul li[data-option], li.dooplay_player_option[data-option]")

        optionElements.amap {
            // Skip the trailer option
            if (it.hasClass("dooplay_player_option_trailer")) return@amap
            val url = it.attr("data-option").trim()
            if (url.isBlank() || url.contains("youtube.com") || url.contains("youtu.be")) return@amap
            loadExtractor(fixHostsLinks(url), mainUrl, subtitleCallback, callback)
        }
        return true
    }
}

fun fixHostsLinks(url: String): String {
    return url
        .replaceFirst("https://hglink.to", "https://streamwish.to")
        .replaceFirst("https://swdyu.com", "https://streamwish.to")
        .replaceFirst("https://cybervynx.com", "https://streamwish.to")
        .replaceFirst("https://dumbalag.com", "https://streamwish.to")
        .replaceFirst("https://mivalyo.com", "https://vidhidepro.com")
        .replaceFirst("https://dinisglows.com", "https://vidhidepro.com")
        .replaceFirst("https://dhtpre.com", "https://vidhidepro.com")
        .replaceFirst("https://filemoon.link", "https://filemoon.sx")
        .replaceFirst("https://sblona.com", "https://watchsb.com")
        .replaceFirst("https://lulu.st", "https://lulustream.com")
        .replaceFirst("https://uqload.io", "https://uqload.com")
        .replaceFirst("https://do7go.com", "https://dood.la")
}
