package com.raava

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.movieproviders.RaavaLiveProvider

@CloudstreamPlugin
class RaavaLivePlugin : Plugin() {
    override fun load(context: Context) {
        // Registro del provider de TV y Deportes en Vivo
        registerMainAPI(RaavaLiveProvider())
    }
}
