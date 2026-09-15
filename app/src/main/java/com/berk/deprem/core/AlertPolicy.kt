package com.berk.deprem.core

import com.berk.deprem.model.Quake

enum class AlertLevel { NONE, INFO, ALARM }

data class Decision(
    val level: AlertLevel,
    val distanceKm: Double,
    val mmi: Double,
    /** true: ses/titresim ile dikkat cek. false: sessizce mevcut bildirimi guncelle. */
    val alert: Boolean,
)

/**
 * "Bu depremi kullaniciya nasil bildirelim?" karari.
 *
 * Temel ilke hiz: ilk kaynak ne diyorsa ona gore bildiririz, uc kaynagin
 * anlasmasini beklemeyiz — beklemek 30-60 saniye kaybettirir. Diger kaynaklar
 * dogruladikca ayni bildirim sessizce guncellenir, yeni bildirim atilmaz.
 */
object AlertPolicy {

    /**
     * Bu olay kullaniciyi hic ilgilendiriyor mu?
     *
     * EMSC WebSocket'i dunya genelindeki her depremi yayinlar; yoklama uclari
     * ise sunucu tarafinda zaten cografi olarak suzuluyor. Bu yuzden suzgec
     * tek bir noktada, verinin depoya girisinde uygulanmali — aksi halde
     * Endonezya depremleri Istanbul listesinde gorunuyor.
     */
    fun isRelevant(lat: Double, lon: Double, settings: Settings): Boolean =
        Geo.distanceKm(settings.homeLat, settings.homeLon, lat, lon) <= settings.radiusKm


    /** Buyukluk revizyonu bu kadar yukari giderse kullaniciyi tekrar uyar. */
    private const val MAG_REALERT_DELTA = 0.4

    /**
     * Buyukluk karsilastirmalarinda kullanilan tolerans.
     *
     * Buyuklukler 0.1 basamagiyla bildirildigi icin bu kadar kucuk bir pay
     * yanlislikla fazladan deprem gecirmez; ama float yuvarlamasinin tam
     * sinirdaki depremi elemesini engeller.
     */
    private const val MAG_EPS = 1e-6

    /** "Bu buyukluk verilen esigi karsiliyor mu?" — esik degeri dahildir. */
    fun meetsThreshold(mag: Double, threshold: Double) = mag >= threshold - MAG_EPS

    fun decide(quake: Quake, settings: Settings, isNew: Boolean): Decision {
        val dist = Geo.distanceKm(settings.homeLat, settings.homeLon, quake.lat, quake.lon)
        val mmi = Intensity.estimateMmi(quake.mag, dist, quake.depthKm)

        val level = when {
            dist > settings.radiusKm -> AlertLevel.NONE
            !meetsThreshold(quake.mag, settings.minMag) -> AlertLevel.NONE
            meetsThreshold(quake.mag, settings.alarmMag) && dist <= settings.alarmRadiusKm ->
                AlertLevel.ALARM
            else -> AlertLevel.INFO
        }

        if (level == AlertLevel.NONE) return Decision(level, dist, mmi, alert = false)

        val alert = when {
            isNew -> true
            // Onceki bildirimden daha yuksek bir seviyeye ciktik (bilgi -> alarm).
            level.ordinal > quake.notifiedLevel -> true
            // Buyukluk kayda deger sekilde yukari revize edildi.
            quake.mag - quake.notifiedMag >= MAG_REALERT_DELTA -> true
            else -> false
        }
        return Decision(level, dist, mmi, alert)
    }
}
