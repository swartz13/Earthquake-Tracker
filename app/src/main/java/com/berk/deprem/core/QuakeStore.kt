package com.berk.deprem.core

import com.berk.deprem.model.Quake
import com.berk.deprem.model.Report
import com.berk.deprem.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import java.util.Locale

/** ingest() sonucu — servis buna bakarak bildirim gonderip gondermeyecegine karar verir. */
sealed interface Ingest {
    /** Filtreye takildi ya da zaten bilinen, degismemis bir cozum. */
    data object Ignored : Ingest

    /** Daha once hic gormedigimiz deprem. */
    data class New(val quake: Quake) : Ingest

    /** Bilinen depremi yeni bir kaynak dogruladi. */
    data class Confirmed(val quake: Quake, val by: Source) : Ingest

    /** Bir kaynak cozumu guncelledi (genelde buyukluk revizyonu). */
    data class Revised(val quake: Quake, val previousMag: Double, val by: Source) : Ingest
}

/**
 * Uc kaynaktan gelen cozumleri tek olay altinda toplar.
 *
 * Eslestirme kurali: iki cozum, olus zamanlari [MATCH_TIME_MS] icindeyse ve
 * episantrlari [matchRadiusKm] icindeyse ayni depremdir. Yaricap buyuklukle
 * birlikte genisler, cunku buyuk depremlerde aglarin konum farki da buyur.
 */
class QuakeStore(private val nowMs: () -> Long = System::currentTimeMillis) {

    companion object {
        /**
         * Gercek veride ayni depremin AFAD/EMSC olus zamanlari 0-1 saniye
         * farkla veriliyor. Buna karsilik artci serilerinde ayni episantrda
         * 40-50 saniye arayla ayri depremler oluyor. Pencere bu iki olcegin
         * arasinda: cozum farkina bol pay birakir, ayri sarsintilari birlestirmez.
         */
        const val MATCH_TIME_MS = 30_000L
        const val RETENTION_MS = 48L * 60 * 60 * 1000
        const val MAX_EVENTS = 300

        /** Buyukluk arttikca eslestirme toleransi acilir: M2 -> 45 km, M6 -> 105 km. */
        fun matchRadiusKm(mag: Double): Double = (35.0 + 10.0 * mag).coerceIn(45.0, 150.0)
    }

    private val mutex = Mutex()
    private val _quakes = MutableStateFlow<List<Quake>>(emptyList())
    val quakes: StateFlow<List<Quake>> = _quakes

    /** Ayni kaynak+id ikilisini tekrar islemeyi ucuza kesmek icin. */
    private val seenKeys = HashSet<String>()

    suspend fun ingest(report: Report): Ingest = mutex.withLock {
        val list = _quakes.value.toMutableList()
        val idx = list.indexOfFirst { matches(it, report) }

        if (idx < 0) {
            val q = Quake(
                id = "${report.source.name}:${report.sourceEventId}:${report.originTimeMs}",
                reports = mapOf(report.source to report),
                firstSeenAtMs = report.receivedAtMs,
                lastUpdatedMs = report.receivedAtMs,
            )
            list.add(q)
            seenKeys.add(key(report))
            publish(list)
            return@withLock Ingest.New(q)
        }

        val existing = list[idx]
        val prev = existing.reports[report.source]

        // Ayni kaynaktan gelen, hicbir seyi degistirmeyen tekrar.
        if (prev != null && !isMeaningfulRevision(prev, report)) {
            seenKeys.add(key(report))
            return@withLock Ingest.Ignored
        }

        val merged = existing.copy(
            reports = existing.reports + (report.source to report),
            lastUpdatedMs = report.receivedAtMs,
        )
        list[idx] = merged
        seenKeys.add(key(report))
        publish(list)

        return@withLock if (prev == null) {
            Ingest.Confirmed(merged, report.source)
        } else {
            Ingest.Revised(merged, prev.mag, report.source)
        }
    }

    /** Bildirim gonderildikten sonra olayin durumunu isaretle ki ayni alarm tekrar calmasin. */
    suspend fun markNotified(quakeId: String, level: Int, mag: Double) = mutex.withLock {
        val list = _quakes.value.toMutableList()
        val idx = list.indexOfFirst { it.id == quakeId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(notifiedLevel = level, notifiedMag = mag)
            _quakes.value = list.sortedByDescending { it.originTimeMs }
        }
    }

    /**
     * Ucuz on-filtre: bu cozumu birebir daha once gorduk mu?
     * Anahtar buyukluk ve derinligi de icerir; aksi halde kaynagin ayni olay
     * kimligiyle yaptigi buyukluk revizyonlari ingest'e hic ulasamaz.
     */
    fun alreadySeen(r: Report) = seenKeys.contains(key(r))

    suspend fun replaceAll(items: List<Quake>) = mutex.withLock {
        seenKeys.clear()
        items.forEach { q -> q.reports.values.forEach { seenKeys.add(key(it)) } }
        publish(items.toMutableList())
    }

    private fun publish(list: MutableList<Quake>) {
        val cutoff = nowMs() - RETENTION_MS
        _quakes.value = list
            .filter { it.originTimeMs >= cutoff }
            .sortedByDescending { it.originTimeMs }
            .take(MAX_EVENTS)
    }

    private fun key(r: Report) =
        "${r.source.name}|${r.sourceEventId}|${r.originTimeMs}|" +
            "${"%.1f".format(Locale.ROOT, r.mag)}|${"%.0f".format(Locale.ROOT, r.depthKm)}|${r.revision}"

    private fun matches(q: Quake, r: Report): Boolean {
        val own = q.reports[r.source]
        if (own != null) {
            // Ayni kaynak ayni olay kimligini verdiyse tartisma yok: bu bir revizyon.
            if (own.sourceEventId == r.sourceEventId) return true
            // Ayni kaynak FARKLI kimlik verdiyse bu ayri bir depremdir. Tek bir ag
            // ayni sarsinti icin iki kimlik uretmez; artci serilerinde saniyeler
            // arayla gelen ayri olaylari birbirinden ayiran kural budur.
            return false
        }

        return q.reports.values.any { other ->
            if (abs(other.originTimeMs - r.originTimeMs) > MATCH_TIME_MS) return@any false
            val d = Geo.distanceKm(other.lat, other.lon, r.lat, r.lon)
            d <= matchRadiusKm(maxOf(other.mag, r.mag))
        }
    }

    /** Kaynak ayni olayi yeniden yayinladi: sadece anlamli degisiklikleri isle. */
    private fun isMeaningfulRevision(prev: Report, next: Report): Boolean =
        next.revision > prev.revision ||
            abs(next.mag - prev.mag) >= 0.1 ||
            abs(next.depthKm - prev.depthKm) >= 1.0 ||
            Geo.distanceKm(prev.lat, prev.lon, next.lat, next.lon) >= 3.0
}
