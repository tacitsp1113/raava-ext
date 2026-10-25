// use an integer for version numbers
version = 4

android {
    namespace = "com.tacit.cloud"
}

cloudstream {
    language = "mx"
    description = "Servicio de reproduccion en la nube en alta definicion."
    authors = listOf("TACIT")

    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )

    iconUrl = "https://raw.githubusercontent.com/tacitsp1113/raava-ext/main/assets/tacitcloud.jpg"
}
