package com.berk.deprem.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import java.util.Locale

object Fmt {
    private val IST: ZoneId = ZoneId.of("Europe/Istanbul")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm")

    /** Tum saatler Istanbul saatiyle gosterilir; kaynaklarin hepsi UTC verir. */
    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(IST).format(TIME)

    fun dateTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(IST).format(DATE_TIME)

    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val s = ((now - ms) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "$s sn once"
            s < 3600 -> "${s / 60} dk once"
            s < 86400 -> "${s / 3600} sa once"
            else -> "${s / 86400} gun once"
        }
    }

    fun km(v: Double): String = "${v.roundToInt()} km"

    fun mag(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

    /** Kaynagin haberi ne kadar gecikmeyle ulastirdigi — hangi ag daha hizli? */
    fun latency(originMs: Long, receivedMs: Long): String {
        val s = ((receivedMs - originMs) / 1000).coerceAtLeast(0)
        return if (s < 120) "${s} sn" else "${s / 60} dk"
    }
}
