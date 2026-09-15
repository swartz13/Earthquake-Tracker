package com.berk.deprem

import android.app.Application
import com.berk.deprem.core.Repo
import com.berk.deprem.service.Notifier
import com.berk.deprem.service.WatchdogWorker

class DepremApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Repo.loadPlaces(this)
        Notifier(this).ensureChannels()
        WatchdogWorker.schedule(this)
    }
}
