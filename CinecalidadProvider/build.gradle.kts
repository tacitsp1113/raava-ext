// use an integer for version numbers
version = 2

cloudstream {
    language = "mx"

    description = "Peliculas y series en alta calidad."
    authors = listOf("TACIT")

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
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=cinecalidad.am&sz=%size%"
}