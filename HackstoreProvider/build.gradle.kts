// use an integer for version numbers
version = 3

android {
    namespace = "com.raava.hackstore"
}

cloudstream {
    language = "mx"
    description = "Películas, Series y Animes en excelente calidad HD y 4K."
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

    iconUrl = "https://raw.githubusercontent.com/tacitsp1113/raava-str/main/logo.png"
}
