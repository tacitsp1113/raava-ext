package com.raava

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.movieproviders.PelisPlusProvider

@CloudstreamPlugin
class PelisPlusProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PelisPlusProvider())
        registerExtractorAPI(VoeExtractor())
    }
}