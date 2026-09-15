package com.berk.deprem.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Time {
    /**
     * AFAD apiv2 "date" alani zaman dilimi soneki olmadan UTC verir
     * (web arayuzu yerel saat gostermesine ragmen). EMSC/Kandilli ile
     * karsilastirilarak dogrulandi.
     */
    private val AFAD_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun parseUtcNoZone(s: String): Long =
        LocalDateTime.parse(s.substringBefore('.').trim().replace(' ', 'T'), AFAD_FMT).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Kandilli lst0.asp metin sayfasinda Europe/Istanbul yerel saatini kullanir: "2026.09.14 13:44:36" */
    private val KOERI_TXT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
    private val ISTANBUL_ZONE = java.time.ZoneId.of("Europe/Istanbul")

    fun parseKoeriLocal(s: String): Long =
        LocalDateTime.parse(s.trim(), KOERI_TXT_FMT).atZone(ISTANBUL_ZONE).toInstant().toEpochMilli()

    /** EMSC: "2026-08-20T21:59:21.9Z" — saniye kesirleri degisken uzunlukta olabiliyor. */
    fun parseIso(s: String): Long = Instant.parse(if (s.endsWith("Z")) s else s + "Z").toEpochMilli()

    fun afadParam(epochMs: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC).format(AFAD_FMT)
}
