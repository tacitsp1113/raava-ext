// use an integer for version numbers
version = 2

android {
    namespace = "com.tacit.cloud"
}

cloudstream {
    language = "mx"
    description = "Catalogo Cloud Instantaneo de mas de 35,000 Peliculas y Series en 4K y 1080p."
    authors = listOf("TACIT")

    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )

    iconUrl = "https://raw.githubusercontent.com/tacitsp1113/raava-ext/main/assets/tacitcloud.jpg"
}
