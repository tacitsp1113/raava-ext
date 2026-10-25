package com.raava

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.movieproviders.CinecalidadProvider

@CloudstreamPlugin
class CinecalidadProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CinecalidadProvider())
        registerExtractorAPI(Vimeos())
        registerExtractorAPI(GoodstreamExtractor())
        registerExtractorAPI(VoeExtractor())
    }
}