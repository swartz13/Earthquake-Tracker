package com.berk.deprem.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.berk.deprem.core.Repo

/**
 * Yeniden baslatma ve uygulama guncellemesi sonrasi izlemeyi geri getirir.
 * BOOT_COMPLETED, arka plandan on plan servisi baslatma kisitindan muaftir.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Repo.init(context)
                if (Repo.prefs.current.monitorEnabled) {
                    runCatching { QuakeMonitorService.start(context) }
                }
            }
        }
    }
}
