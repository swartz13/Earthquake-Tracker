package com.berk.deprem.model

/** Verinin geldigi sismik agi. Siralama = guven onceligi (ustteki tercih edilir). */
enum class Source(val label: String, val short: String, val priority: Int) {
    AFAD("AFAD", "AFAD", 0),
    KOERI("Kandilli", "KOERI", 1),
    EMSC("EMSC", "EMSC", 2);
}

/**
 * Tek bir kaynagin tek bir deprem icin verdigi cozum.
 * Ayni deprem icin uc kaynaktan uc farkli [Report] gelir; bunlari [Quake] birlestirir.
 */
data class Report(
    val source: Source,
    val sourceEventId: String,
    val originTimeMs: Long,   // depremin olus ani, UTC epoch millis
    val lat: Double,
    val lon: Double,
    val depthKm: Double,
    val mag: Double,
    val magType: String,
    val region: String,
    val receivedAtMs: Long,   // bizim haberdar oldugumuz an
    val revision: Int = 0,    // kaynak cozumu guncellediyse artar
    /**
     * Bu cozumu deprem olurken izlerken mi aldik?
     *
     * Uygulama acildiginda son 45 dakika geriye donuk cekiliyor. O kayitlarda
     * [receivedAtMs] - [originTimeMs] farki tespit gecikmesini degil,
     * uygulamanin ne zaman acildigini olcer. Ikisini karistirmamak icin
     * ayirt ediyoruz: yalnizca live=true olanlarda gecikme anlamli.
     */
    val live: Boolean = true,
)

/**
 * Birden fazla kaynaktan eslesmis tek deprem olayi.
 * [reports] her kaynaktan en guncel cozumu tutar.
 */
data class Quake(
    val id: String,
    val reports: Map<Source, Report>,
    val firstSeenAtMs: Long,
    val lastUpdatedMs: Long,
    /** Kullaniciya en son hangi seviyede bildirim gitti (bkz. AlertLevel.ordinal). */
    val notifiedLevel: Int = -1,
    val notifiedMag: Double = 0.0,
) {
    /** Gosterimde kullanilacak cozum: yerel aglar (AFAD/Kandilli) EMSC'ye tercih edilir. */
    val best: Report get() = reports.values.minBy { it.source.priority }

    /** En hizli haber veren kaynak — "ilk kim duyurdu" istatistigi icin. */
    val fastest: Report get() = reports.values.minBy { it.receivedAtMs }

    val mag: Double get() = best.mag
    val lat: Double get() = best.lat
    val lon: Double get() = best.lon
    val depthKm: Double get() = best.depthKm
    val originTimeMs: Long get() = best.originTimeMs
    val region: String get() = reports.values.firstOrNull { it.region.isNotBlank() }?.region ?: "Bilinmiyor"
    val confirmCount: Int get() = reports.size

    /** Gercek tespit gecikmesi olculebiliyor mu (izleme sirasinda mi geldi). */
    val latencyMeasurable: Boolean get() = reports.values.any { it.live }

    /** Gecikme olcumu icin yalnizca canli alinan cozumler sayilir. */
    val fastestLive: Report? get() = reports.values.filter { it.live }.minByOrNull { it.receivedAtMs }

    /** Kaynaklar arasindaki en buyuk buyukluk farki — cozumler ne kadar oturmus? */
    val magSpread: Double
        get() = if (reports.size < 2) 0.0
        else reports.values.maxOf { it.mag } - reports.values.minOf { it.mag }
}
