package com.berk.deprem.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.berk.deprem.core.AlertLevel
import com.berk.deprem.core.AlertPolicy
import com.berk.deprem.core.Ingest
import com.berk.deprem.core.LocationStatus
import com.berk.deprem.core.Repo
import com.berk.deprem.data.AfadSource
import com.berk.deprem.data.EmscSource
import com.berk.deprem.data.EmscWebSocket
import com.berk.deprem.data.KoeriSource
import com.berk.deprem.data.PollSource
import com.berk.deprem.model.Report
import com.berk.deprem.model.Source
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Uc kaynagi kesintisiz dinleyen on plan servisi.
 *
 * Neden on plan servisi: bildirimin gecikmesi bu uygulamanin tek olcutu.
 * WorkManager'in en sik periyodu 15 dakika, JobScheduler uykuda ertelenir —
 * ikisi de "deprem oldu, hemen haber ver" isi icin uygun degil. Kalici bildirim
 * bedeli karsiliginda sureklu acik bir WebSocket ve saniyelik yoklama aliyoruz.
 */
class QuakeMonitorService : Service() {

    companion object {
        private const val TAG = "Monitor"
        const val ACTION_START = "com.berk.deprem.START"
        const val ACTION_STOP = "com.berk.deprem.STOP"

        /** Yoklamalarin geriye dogru bakacagi pencere; bir kopusta veri kaybini onler. */
        private const val LOOKBACK_MS = 45L * 60 * 1000

        /** statusLoop 15 saniyede bir yeniledigi icin bol pay birakiliyor. */
        private const val WAKELOCK_MS = 30L * 60 * 1000

        fun start(ctx: Context) {
            val i = Intent(ctx, QuakeMonitorService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, QuakeMonitorService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = CoroutineExceptionHandler { _, e -> Log.e(TAG, "yakalanmamis hata", e) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    private lateinit var notifier: Notifier
    private var wakeLock: PowerManager.WakeLock? = null
    private var jobs = mutableListOf<Job>()
    private var running = false

    /**
     * Izlemenin basladigi an. Bundan onceki depremler geriye donuk dolgudur;
     * onlarda "kac saniyede haber aldik" olcumu anlamsizdir.
     */
    private var sessionStartMs = 0L
    private var networkWatcher: NetworkWatcher? = null
    private var locationFinder: LocationFinder? = null

    private val ingestLock = Mutex()

    private val emscWs by lazy {
        EmscWebSocket { report -> handleReport(report) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Repo.loadPlaces(this)
        notifier = Notifier(this)
        notifier.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        if (!running) {
            running = true
            Repo.serviceRunning.value = true
            startMonitoring()
        }
        // Sistem servisi oldururse yeniden baslatilsin.
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val s = com.berk.deprem.ui.Strings.get(Repo.prefs.current.language)
        val n = notifier.buildForeground(s.serviceNotificationActive, s.serviceNotificationConnecting)
        // Konum tipini de bildirmek zorunlu: aksi halde servis calisirken
        // yapilan konum istekleri "arka plan" sayilip reddediliyor.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var tipler = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                tipler = tipler or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            if (LocationFinder(this).hasPermission()) {
                tipler = tipler or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (tipler != 0) {
                startForeground(Notifier.FG_NOTIFICATION_ID, n, tipler)
            } else {
                startForeground(Notifier.FG_NOTIFICATION_ID, n)
            }
        } else {
            startForeground(Notifier.FG_NOTIFICATION_ID, n)
        }
    }

    private fun startMonitoring() {
        sessionStartMs = System.currentTimeMillis()
        networkWatcher = NetworkWatcher(this).also { it.register() }
        locationFinder = LocationFinder(this)
        // Kismi wake lock: Doze sirasinda soketin ve yoklamalarin donmasini geciktirir.
        // Suresiz degil sureli aliniyor ve statusLoop her turda yeniliyor; servis
        // beklenmedik bir sekilde sizarsa kilit kendiliginden dusuyor.
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "deprem:monitor")
            .apply { setReferenceCounted(false) }
        renewWakeLock()

        jobs += scope.launch { Repo.load() }

        if (Repo.prefs.current.emscEnabled) {
            jobs += emscWs.start(scope, Repo.networkGeneration)
            jobs += pollLoop(EmscSource) { Repo.prefs.current.emscPollSec }
        }
        if (Repo.prefs.current.afadEnabled) {
            jobs += pollLoop(AfadSource) { Repo.prefs.current.afadPollSec }
        }
        if (Repo.prefs.current.koeriEnabled) {
            jobs += pollLoop(KoeriSource) { Repo.prefs.current.koeriPollSec }
        }

        jobs += statusLoop()
        jobs += locationLoop()
        jobs += socketStatusLoop()
    }

    /**
     * Tek bir kaynagi belirli araliklarla yoklar. Hata durumunda araligi
     * gecici olarak acar ki dusmus bir servis pil yakmasin.
     */
    private fun pollLoop(src: PollSource, intervalSec: () -> Int) = scope.launch {
        var consecutiveErrors = 0
        while (isActive) {
            val started = System.currentTimeMillis()
            val agKusagi = Repo.networkGeneration.value
            try {
                val reports = src.fetch(Repo.prefs.current, started - LOOKBACK_MS)
                var fresh = 0
                for (r in reports) {
                    if (Repo.store.alreadySeen(r)) continue
                    fresh++
                    handleReport(r)
                }
                Repo.markSuccess(src.source, fresh)
                consecutiveErrors = 0
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isActive) break
                Repo.markError(src.source, e)
                consecutiveErrors++
                Log.w(TAG, "${src.source.short} yoklamasi basarisiz: ${e.message}")
            }

            val base = intervalSec().coerceIn(5, 600) * 1000L
            // Ustel geri cekilme, en fazla 8 kat.
            val backoff = base * (1 shl consecutiveErrors.coerceAtMost(3))
            val elapsed = System.currentTimeMillis() - started
            val bekle = (backoff - elapsed).coerceAtLeast(1000L)

            // Bekleme sirasinda ag degisirse beklemeyi kes: kullanici wifi'ye
            // gectiginde ya da VPN'i kapattiginda saniyeler icinde toparla.
            val agDegisti = withTimeoutOrNull(bekle) {
                Repo.networkGeneration.first { it != agKusagi }
            } != null
            if (agDegisti) {
                consecutiveErrors = 0
                Log.i(TAG, "${src.source.short}: ag degisti, hemen yeniden deneniyor")
            }
        }
    }

    @Suppress("WakelockTimeout")
    private fun renewWakeLock() {
        runCatching { wakeLock?.acquire(WAKELOCK_MS) }
    }

    /** WebSocket baglanti durumunu UI rozetlerine tasir. */
    private fun socketStatusLoop() = scope.launch {
        emscWs.connected.collect { live ->
            Repo.mark(Source.EMSC) { it.copy(liveSocket = live) }
        }
    }

    /** Kalici bildirimi guncel tutar ve veriyi diske yazar. */
    private fun statusLoop() = scope.launch {
        while (isActive) {
            delay(15_000)
            renewWakeLock()
            val st = Repo.status.value
            val now = System.currentTimeMillis()
            val lang = Repo.prefs.current.language
            val appStrings = com.berk.deprem.ui.Strings.get(lang)
            val parts = Source.entries.map { s ->
                val info = st[s] ?: return@map "${s.short} ?"
                when {
                    s == Source.EMSC && emscWs.connected.value -> "${s.short} ${appStrings.live}"
                    info.lastSuccessMs == 0L -> "${s.short} –"
                    info.lastError != null -> "${s.short} ${appStrings.error}"
                    else -> "${s.short} ${appStrings.secondsAgo(((now - info.lastSuccessMs) / 1000).coerceAtLeast(0))}"
                }
            }
            val count = Repo.store.quakes.value.size
            val cevrimici = Repo.online.value
            val baslik = when {
                !cevrimici -> appStrings.serviceNotificationOffline
                emscWs.connected.value -> appStrings.serviceNotificationEmscLive
                else -> appStrings.serviceNotificationActive
            }
            val govde = if (!cevrimici) {
                if (lang == "en") "Waiting for connection — will resume automatically when online"
                else "Baglanti bekleniyor — ag gelir gelmez kendiliginden devam eder"
            } else {
                parts.joinToString(" · ")
            }
            val eventsLabel = if (lang == "en") "24h events: $count · radius ${Repo.prefs.current.radiusKm.roundToInt()} km"
            else "24 saatte $count olay · yaricap ${Repo.prefs.current.radiusKm.roundToInt()} km"
            notifier.updateForeground(
                baslik,
                "$govde\n$eventsLabel"
            )
            Repo.save()
        }
    }

    /**
     * "Konumumu kullan" ayarini ve elle yenileme istegini dinler.
     *
     * Eskiden yalnizca 10 dakikada bir bakiyordu; kullanici ayari acip
     * kapattiginda hicbir sey olmuyor gibi gorunuyordu. Simdi ayar
     * degisimi aninda tetikliyor.
     */
    private fun locationLoop() = scope.launch {
        combine(
            Repo.prefs.state.map { it.useDeviceLocation }.distinctUntilChanged(),
            Repo.locationRefresh,
        ) { acik, _ -> acik }
            .collectLatest { acik ->
                if (!acik) {
                    Repo.locationStatus.value = null
                    Repo.prefs.update { it.copy(homeFromDevice = false) }
                    return@collectLatest
                }
                refreshLocation()
                while (currentCoroutineContext().isActive) {
                    delay(10 * 60_000)
                    refreshLocation()
                }
            }
    }

    private suspend fun refreshLocation() {
        val finder = locationFinder ?: return
        if (!finder.hasPermission()) {
            Repo.locationStatus.value = LocationStatus.IZIN_YOK
            Log.w(TAG, "konum izni yok")
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager
        if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) {
            Repo.locationStatus.value = LocationStatus.SERVIS_KAPALI
            return
        }

        Repo.locationStatus.value = LocationStatus.ARANIYOR
        val loc = finder.current()
        if (loc != null) {
            Repo.prefs.update {
                it.copy(homeLat = loc.latitude, homeLon = loc.longitude, homeFromDevice = true)
            }
            Repo.locationStatus.value = null
            Log.i(TAG, "konum guncellendi: ${loc.latitude}, ${loc.longitude}")
            // Referans noktasi degisti: yaricap disina dusen kayitlar temizlensin.
            Repo.bumpNetwork()
        } else {
            Repo.locationStatus.value = LocationStatus.BULUNAMADI
            Log.w(TAG, "konum bulunamadi")
        }
    }

    /** Tum kaynaklar buradan gecer: suz, birlestir, karar ver, bildir. */
    private fun handleReport(raw: Report) {
        if (!AlertPolicy.isRelevant(raw.lat, raw.lon, Repo.prefs.current)) return
        // Izleme baslamadan once olmus depremler geriye donuk dolgudur.
        val report = raw.copy(live = raw.originTimeMs >= sessionStartMs)
        scope.launch {
            ingestLock.withLock {
                when (val result = Repo.store.ingest(report)) {
                    is Ingest.Ignored -> Unit

                    is Ingest.New -> {
                        Repo.mark(report.source) { it.copy(firstReports = it.firstReports + 1) }
                        evaluate(result.quake, isNew = true)
                    }

                    is Ingest.Confirmed -> evaluate(result.quake, isNew = false)

                    is Ingest.Revised -> evaluate(result.quake, isNew = false)
                }
            }
        }
    }

    private suspend fun evaluate(quake: com.berk.deprem.model.Quake, isNew: Boolean) {
        val decision = AlertPolicy.decide(quake, Repo.prefs.current, isNew)
        if (decision.level == AlertLevel.NONE) return

        // Esigi gecmeyen ama daha once bildirilmis bir olayin sessiz guncellemesi de
        // gonderilir; kart uzerindeki "2/3 kaynak" bilgisi boyle tazelenir.
        val alreadyNotified = quake.notifiedLevel >= 0
        if (!isNew && !decision.alert && !alreadyNotified) return

        notifier.notifyQuake(quake, decision)
        Repo.store.markNotified(quake.id, decision.level.ordinal, quake.mag)
        Repo.save()
    }

    private fun stopMonitoring() {
        running = false
        Repo.serviceRunning.value = false
        networkWatcher?.unregister()
        networkWatcher = null
        emscWs.close()
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { wakeLock?.release() }
        wakeLock = null
        Repo.save()
    }

    override fun onDestroy() {
        stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Kullanici uygulamayi son kullanilanlardan attiginda izleme surmeli.
        if (Repo.prefs.current.monitorEnabled) {
            start(this)
        }
        super.onTaskRemoved(rootIntent)
    }
}
