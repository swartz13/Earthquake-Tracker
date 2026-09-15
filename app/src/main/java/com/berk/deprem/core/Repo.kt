package com.berk.deprem.core

import android.content.Context
import com.berk.deprem.model.Quake
import com.berk.deprem.model.Report
import com.berk.deprem.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bir kaynagin anlik sagligi — UI'daki rozetler ve kalici bildirim bunu okur. */
data class SourceStatus(
    val lastSuccessMs: Long = 0,
    val lastError: String? = null,
    val liveSocket: Boolean = false,
    val eventsSeen: Int = 0,
    /** Bu kaynak kac depremde ilk haber veren oldu. */
    val firstReports: Int = 0,
)

/**
 * Uygulama capinda tek ornek. Servis ve UI ayni verilere baksin diye
 * Application yasam dongusune bagli.
 */
object Repo {
    lateinit var prefs: Prefs
        private set
    val store = QuakeStore()

    private val _status = MutableStateFlow(
        Source.entries.associateWith { SourceStatus() }
    )
    val status: StateFlow<Map<Source, SourceStatus>> = _status

    val serviceRunning = MutableStateFlow(false)

    /** Dogrulanmis internet erisimi var mi (ConnectivityManager'a gore). */
    val online = MutableStateFlow(true)

    /**
     * Her ag degisiminde (ve elle yenilemede) artar.
     *
     * Yoklama donguleri bekleme sirasinda bu degeri izler: deger degisince
     * beklemeyi kesip hemen yeniden dener. Aksi halde ag geri geldiginde
     * ustel geri cekilmenin dolmasini beklemek gerekiyordu — kotu durumda
     * iki dakika.
     */
    val networkGeneration = MutableStateFlow(0)

    fun bumpNetwork() { networkGeneration.value += 1 }

    /** Elle "konumu simdi bul" istegi; artirmak servisi tetikler. */
    val locationRefresh = MutableStateFlow(0)

    fun requestLocationRefresh() { locationRefresh.value += 1 }

    /**
     * Konum neden alinamadi. null = sorun yok. Arayuz "bekleniyor" demek
     * yerine gercek sebebi gosterebilsin diye tutuluyor.
     */
    val locationStatus = MutableStateFlow<LocationStatus?>(null)

    /**
     * Yerlesim verisi hazir mi. 700 KB'lik liste ana is parcaciginda
     * ayristirilirsa acilista takilma olur; arka planda yukleyip bittiginde
     * arayuzu yeniden cizdiriyoruz.
     */
    val placesReady = MutableStateFlow(false)

    fun loadPlaces(ctx: Context) {
        if (Places.loaded) { placesReady.value = true; return }
        Thread {
            runCatching { Places.load(ctx.applicationContext.assets.open("yerlesimler.tsv")) }
            placesReady.value = Places.loaded
        }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private lateinit var cacheFile: File

    private val loadOnce = Mutex()
    @Volatile private var loaded = false

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = Prefs(ctx.applicationContext)
        cacheFile = File(ctx.applicationContext.filesDir, "quakes.json")
    }

    fun mark(source: Source, block: (SourceStatus) -> SourceStatus) {
        _status.value = _status.value.toMutableMap().apply {
            this[source] = block(this[source] ?: SourceStatus())
        }
    }

    fun markSuccess(source: Source, count: Int) = mark(source) {
        it.copy(lastSuccessMs = System.currentTimeMillis(), lastError = null, eventsSeen = it.eventsSeen + count)
    }

    fun markError(source: Source, e: Throwable) = mark(source) {
        it.copy(lastError = e.message?.take(120) ?: e.javaClass.simpleName)
    }

    // --- Kalicilik: uygulama kapanip acildiginda liste bos gorunmesin ---

    /**
     * Diskteki anlik goruntuyu bir kez yukler. Hem MainActivity hem servis
     * cagiriyor; ikinci cagrinin canli listeyi eski veriyle ezmemesi sart.
     */
    suspend fun load() = loadOnce.withLock {
        if (loaded) return@withLock
        loaded = true
        val f = cacheFile
        if (!f.exists()) return@withLock
        runCatching {
            val arr = JSONArray(f.readText())
            val items = (0 until arr.length()).mapNotNull { i ->
                runCatching { quakeFromJson(arr.getJSONObject(i)) }.getOrNull()
            }.filter { AlertPolicy.isRelevant(it.lat, it.lon, prefs.current) }
            store.replaceAll(items)
        }
    }

    fun save() {
        runCatching {
            val arr = JSONArray()
            store.quakes.value.take(150).forEach { arr.put(quakeToJson(it)) }
            cacheFile.writeText(arr.toString())
        }
    }

    private fun quakeToJson(q: Quake) = JSONObject().apply {
        put("id", q.id)
        put("firstSeen", q.firstSeenAtMs)
        put("lastUpdated", q.lastUpdatedMs)
        put("notifiedLevel", q.notifiedLevel)
        put("notifiedMag", q.notifiedMag)
        put("reports", JSONArray().apply {
            q.reports.values.forEach { r ->
                put(JSONObject().apply {
                    put("src", r.source.name); put("sid", r.sourceEventId)
                    put("t", r.originTimeMs); put("lat", r.lat); put("lon", r.lon)
                    put("d", r.depthKm); put("m", r.mag); put("mt", r.magType)
                    put("reg", r.region); put("rx", r.receivedAtMs); put("rev", r.revision)
                    put("live", r.live)
                })
            }
        })
    }

    private fun quakeFromJson(o: JSONObject): Quake {
        val arr = o.getJSONArray("reports")
        val reports = (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            Report(
                source = Source.valueOf(r.getString("src")),
                sourceEventId = r.getString("sid"),
                originTimeMs = r.getLong("t"),
                lat = r.getDouble("lat"), lon = r.getDouble("lon"),
                depthKm = r.getDouble("d"), mag = r.getDouble("m"),
                magType = r.optString("mt", "M"), region = r.optString("reg", ""),
                receivedAtMs = r.optLong("rx", o.getLong("firstSeen")),
                revision = r.optInt("rev", 0),
                live = r.optBoolean("live", false),
            )
        }.associateBy { it.source }
        return Quake(
            id = o.getString("id"),
            reports = reports,
            firstSeenAtMs = o.getLong("firstSeen"),
            lastUpdatedMs = o.getLong("lastUpdated"),
            notifiedLevel = o.optInt("notifiedLevel", -1),
            notifiedMag = o.optDouble("notifiedMag", 0.0),
        )
    }
}

/** Konum alinamiyorsa sebebi — arayuz "bekleniyor" yerine bunu gosterir. */
enum class LocationStatus(val label: String) {
    ARANIYOR("konum araniyor"),
    IZIN_YOK("konum izni verilmedi"),
    SERVIS_KAPALI("cihazda konum kapali"),
    BULUNAMADI("konum alinamadi"),
}
