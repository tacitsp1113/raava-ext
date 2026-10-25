package com.raava

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.movieproviders.CuevanaProvider

@CloudstreamPlugin
class CuevanaProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CuevanaProvider())
        registerExtractorAPI(VoeExtractor())
    }
}