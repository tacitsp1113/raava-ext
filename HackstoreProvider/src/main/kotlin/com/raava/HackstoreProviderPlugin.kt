package com.raava

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.movieproviders.HackstoreProvider

@CloudstreamPlugin
class HackstoreProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HackstoreProvider())
    }
}
