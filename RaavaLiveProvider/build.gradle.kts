// use an integer for version numbers
version = 4

android {
    namespace = "com.raava.live"
}

cloudstream {
    language = "mx"
    description = "Deportes y Televisión en Vivo 24/7 (ESPN, Fox Sports, TUDN, Star+, Azteca 7)."
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
        "Live"
    )

    iconUrl = "https://raw.githubusercontent.com/tacitsp1113/raava-str/main/logo.png"
}
