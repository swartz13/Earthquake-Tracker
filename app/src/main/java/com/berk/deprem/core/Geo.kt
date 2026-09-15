package com.berk.deprem.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_R_KM = 6371.0088

    /** Iki nokta arasi buyuk daire mesafesi (km). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_R_KM * asin(min(1.0, sqrt(a)))
    }

    /** Odaga (hiposantr) uzaklik: yuzey mesafesi + derinlik birlikte. */
    fun hypocentralKm(surfaceKm: Double, depthKm: Double): Double =
        sqrt(surfaceKm * surfaceKm + depthKm * depthKm)

    fun bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val p1 = Math.toRadians(fromLat)
        val p2 = Math.toRadians(toLat)
        val dl = Math.toRadians(toLon - fromLon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    /** 8 yonlu pusula yonu: "kuzeydogusunda" gibi metin uretmek icin. */
    fun bearingLabel(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
        val deg = bearingDegrees(fromLat, fromLon, toLat, toLon)
        val names = listOf("kuzey", "kuzeydogu", "dogu", "guneydogu", "guney", "guneybati", "bati", "kuzeybati")
        return names[(((deg + 22.5) % 360) / 45).toInt()]
    }
}

/**
 * Kaba hissedilen siddet (MMI) tahmini — sadece "bu deprem beni ilgilendirir mi?"
 * ayrimi icin. Bilimsel bir siddet haritasi degildir, zemin etkisini icermez.
 * Basit log-mesafe sonumlenmesi (Bakun-Wentworth tipi bir yaklasim).
 */
object Intensity {
    fun estimateMmi(mag: Double, surfaceKm: Double, depthKm: Double): Double {
        val r = Geo.hypocentralKm(surfaceKm, depthKm).coerceAtLeast(4.0)
        val mmi = 1.7 + 1.5 * mag - 1.1726 * log10(r) - 0.00397 * r
        return mmi.coerceIn(1.0, 12.0)
    }

    fun label(mmi: Double): String = when {
        mmi < 2.0 -> "hissedilmez"
        mmi < 3.5 -> "zar zor hissedilir"
        mmi < 4.5 -> "hafif hissedilir"
        mmi < 5.5 -> "belirgin sallanti"
        mmi < 6.5 -> "guclu sallanti"
        mmi < 7.5 -> "cok guclu"
        else -> "siddetli"
    }

    fun roman(mmi: Double): String {
        val i = mmi.toInt().coerceIn(1, 12)
        return listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")[i - 1]
    }
}
