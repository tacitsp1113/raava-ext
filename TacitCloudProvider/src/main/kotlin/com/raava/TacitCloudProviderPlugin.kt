package com.raava

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.movieproviders.TacitCloudProvider

@CloudstreamPlugin
class TacitCloudProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TacitCloudProvider())
    }
}
