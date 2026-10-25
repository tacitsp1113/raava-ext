package com.raava

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HackstoreProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HackstoreProvider())
        registerExtractorAPI(VimeosExtractor())
        registerExtractorAPI(VideoApp())
        registerExtractorAPI(Goodstream())
        registerExtractorAPI(Hlswish())
        registerExtractorAPI(VoeExtractor())
    }
}