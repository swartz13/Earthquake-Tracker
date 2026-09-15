package com.berk.deprem.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.berk.deprem.core.Repo
import java.util.concurrent.TimeUnit

/**
 * Servis oldurulurse (uretici pil optimizasyonu, dusuk bellek) geri getirir.
 * WorkManager'in 15 dakikalik alt siniri bildirim icin cok yavas — bu yuzden
 * watchdog bildirim yolu degil, yalnizca kurtarma agi.
 */
class WatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        if (Repo.prefs.current.monitorEnabled && !Repo.serviceRunning.value) {
            // Arka plandan dogrudan on plan servisi baslatmak Android 12+ ile kisitli.
            // Hizlandirilmis (expedited) is bu kisittan muaf oldugu icin restart'i
            // ona devrediyoruz.
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                RESTART_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RestartWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            )
        }
        return Result.success()
    }

    companion object {
        private const val WATCHDOG_WORK = "deprem_watchdog"
        private const val RESTART_WORK = "deprem_restart"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WATCHDOG_WORK, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WATCHDOG_WORK)
        }
    }
}

class RestartWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        if (Repo.prefs.current.monitorEnabled && !Repo.serviceRunning.value) {
            runCatching { QuakeMonitorService.start(applicationContext) }
                .onFailure { return Result.retry() }
        }
        return Result.success()
    }
}
