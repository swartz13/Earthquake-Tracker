package com.berk.deprem.core

import com.berk.deprem.ui.AppStrings
import java.io.InputStream
import kotlin.math.roundToInt

/** Gomulu yerlesim listesinden tek bir kayit. */
data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    val population: Int,
    val province: String,
    val country: String,
) {
    /** "Simav (Kütahya)" — il adi ayni ise tekrar etme. */
    val label: String
        get() = if (province.isNotBlank() && !province.equals(name, ignoreCase = true))
            "$name ($province)" else name
}

/** Bir episantrin insan tarafindan okunabilir konum tarifi. */
data class PlaceInfo(
    /** "Simav'ın 17 km kuzeyinde" (Turkce varsayilan) */
    val headline: String,
    /** En yakin yerlesim. */
    val nearest: Place?,
    val nearestKm: Double,
    val bearingDegrees: Double = 0.0,
    /** En yakin buyuk sehir (varsa ve en yakin yerlesimden farkliysa). */
    val city: Place?,
    val cityKm: Double,
) {
    fun localizedHeadline(s: AppStrings): String {
        val place = nearest ?: return s.locationUnavailable
        return if (nearestKm < 3.0) {
            s.inCenterOf(place.label)
        } else {
            val dir = s.compassBearing(bearingDegrees)
            s.directionFrom(place.name, nearestKm.roundToInt(), dir)
        }
    }
}

/**
 * Episantrlari yerlesim adlarina cevirir.
 *
 * Neden gerekli: EMSC yalnizca Flynn-Engdahl bolge kodu veriyor —
 * "WESTERN TURKEY" gibi. Nerede oldugunu soylemiyor. EMSC'nin kendi sitesi
 * "17 km N of Simav" bilgisini kendi yerlesim veritabanindan hesapliyor;
 * biz de ayni seyi cihazda yapiyoruz.
 *
 * Veri: GeoNames cities1000 (CC BY 4.0), 32-48°K / 18-48°D penceresine
 * kirpilmis hali. Cevrimdisi calisir, ag gecikmesi eklemez.
 */
object Places {

    /** Bu nufusun ustundekiler "tanidik sehir" sayilir ve ikinci referans olur. */
    private const val CITY_POP = 100_000

    private var names: Array<String> = emptyArray()
    private var provinces: Array<String> = emptyArray()
    private var countries: Array<String> = emptyArray()
    private var lats = DoubleArray(0)
    private var lons = DoubleArray(0)
    private var pops = IntArray(0)

    @Volatile var loaded = false
        private set

    /** Ayristirma ag/Android'den bagimsiz olsun diye InputStream aliyor. */
    @Synchronized
    fun load(input: InputStream) {
        if (loaded) return
        val n = ArrayList<String>(16000)
        val p = ArrayList<String>(16000)
        val c = ArrayList<String>(16000)
        val la = ArrayList<Double>(16000)
        val lo = ArrayList<Double>(16000)
        val po = ArrayList<Int>(16000)

        input.bufferedReader().forEachLine { line ->
            val f = line.split('\t')
            if (f.size >= 6) {
                val lat = f[1].toDoubleOrNull()
                val lon = f[2].toDoubleOrNull()
                if (lat != null && lon != null) {
                    n.add(f[0]); la.add(lat); lo.add(lon)
                    po.add(f[3].toIntOrNull() ?: 0); p.add(f[4]); c.add(f[5])
                }
            }
        }

        names = n.toTypedArray(); provinces = p.toTypedArray(); countries = c.toTypedArray()
        lats = la.toDoubleArray(); lons = lo.toDoubleArray(); pops = po.toIntArray()
        loaded = true
    }

    val size: Int get() = names.size

    private fun at(i: Int) = Place(names[i], lats[i], lons[i], pops[i], provinces[i], countries[i])

    /**
     * En yakin yerlesimi bulur. 15 bin kayitta duz tarama ~1 ms suruyor;
     * mekansal indeks eklemek karmasikligi hak etmiyor.
     */
    fun nearest(lat: Double, lon: Double, minPop: Int = 0): Pair<Place, Double>? {
        var bestIdx = -1
        var bestKm = Double.MAX_VALUE
        for (i in names.indices) {
            if (pops[i] < minPop) continue
            // Once ucuz bir on eleme: kaba kare mesafe.
            val dLat = lats[i] - lat
            if (dLat > 9.0 || dLat < -9.0) continue
            val d = Geo.distanceKm(lat, lon, lats[i], lons[i])
            if (d < bestKm) { bestKm = d; bestIdx = i }
        }
        return if (bestIdx < 0) null else at(bestIdx) to bestKm
    }

    /**
     * EMSC'nin sitedeki anlatimiyla ayni yon mantigi: yon, YERLESIMDEN
     * episantra dogru olculur. "17 km N of Simav" = Simav'in 17 km kuzeyinde.
     */
    fun describe(lat: Double, lon: Double): PlaceInfo {
        val near = nearest(lat, lon)
        val big = nearest(lat, lon, CITY_POP)

        if (near == null) {
            return PlaceInfo("Konum belirlenemedi", null, 0.0, 0.0, null, 0.0)
        }
        val (place, km) = near
        val deg = Geo.bearingDegrees(place.lat, place.lon, lat, lon)
        val yon = Turkish.direction(place.lat, place.lon, lat, lon)
        val headline = when {
            km < 3.0 -> "${place.label} merkezinde"
            else -> "${Turkish.genitive(place.name)} ${km.toInt()} km $yon"
        }
        val city = big?.first
        val cityKm = big?.second ?: 0.0
        return PlaceInfo(
            headline = headline,
            nearest = place,
            nearestKm = km,
            bearingDegrees = deg,
            city = if (city != null && city.name != place.name) city else null,
            cityKm = cityKm,
        )
    }
}
