// use an integer for version numbers
version = 1

android {
    namespace = "com.raava.hackstore"
}

cloudstream {
    language = "mx"
    description = "Películas, Series y Animes en excelente calidad desde Hackstore2."
    authors = listOf("Raava")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=hackstore2.com&sz=%size%"
}
