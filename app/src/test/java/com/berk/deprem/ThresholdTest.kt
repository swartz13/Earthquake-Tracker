package com.berk.deprem

import com.berk.deprem.core.AlertLevel
import com.berk.deprem.core.AlertPolicy
import com.berk.deprem.core.Settings
import com.berk.deprem.model.Quake
import com.berk.deprem.model.Report
import com.berk.deprem.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Esik anlaminin sozlesmesi: "esik = 3.0" demek "M3.0 ve UZERI bildirilir" demektir.
 * Sinir degerin kendisi dahildir.
 */
class ThresholdTest {

    private fun quakeOf(mag: Double, lat: Double = 41.02, lon: Double = 28.99) = Quake(
        id = "t$mag",
        reports = mapOf(
            Source.AFAD to Report(
                source = Source.AFAD, sourceEventId = "e", originTimeMs = 0L,
                lat = lat, lon = lon, depthKm = 10.0, mag = mag,
                magType = "ML", region = "Test", receivedAtMs = 0L,
            )
        ),
        firstSeenAtMs = 0L, lastUpdatedMs = 0L,
    )

    private val s = Settings(
        homeLat = 41.0082, homeLon = 28.9784,
        radiusKm = 300.0, minMag = 3.0, alarmMag = 4.0, alarmRadiusKm = 150.0,
    )

    private fun level(mag: Double, settings: Settings = s) =
        AlertPolicy.decide(quakeOf(mag), settings, isNew = true).level

    @Test
    fun `esik degerinin kendisi bildirilir`() {
        assertEquals(AlertLevel.NONE, level(2.9))
        assertEquals(AlertLevel.INFO, level(3.0))
        assertEquals(AlertLevel.INFO, level(3.1))
        assertEquals(AlertLevel.INFO, level(3.9))
    }

    @Test
    fun `alarm esiginin kendisi alarm calar`() {
        assertEquals(AlertLevel.INFO, level(3.9))
        assertEquals(AlertLevel.ALARM, level(4.0))
        assertEquals(AlertLevel.ALARM, level(5.7))
    }

    /**
     * Ayarlar SharedPreferences'ta Float olarak saklaniyor. Kaydiriciyla secilen
     * 3.0, Float'a girip ciktiginda 3.0000002 olabilir; o durumda M3.0'lik bir
     * deprem sessizce elenirdi ve ekranda yine "M3,0" yazdigi icin sebebi
     * anlasilmazdi. Ayni yuvarlama yolunu burada birebir tekrarliyoruz.
     */
    @Test
    fun `float yuvarlamasi sinir degeri kaydirmaz`() {
        for (raw in listOf(2.5f, 3.0f, 3.5f, 4.0f, 4.5f, 1.0f + 5.0f * (20f / 50f))) {
            val roundTripped = raw.toDouble()
            val settings = s.copy(minMag = roundTripped, alarmMag = 9.0)
            assertEquals(
                "Esik $roundTripped iken tam sinirdaki M$raw bildirilmeli",
                AlertLevel.INFO,
                level(raw.toDouble(), settings),
            )
            // Depremin buyuklugu kaynaktan Double olarak geliyor; esik Float'tan.
            // Iki farkli yoldan gelen ayni sayi karsilastiriliyor.
            assertEquals(
                "Esik $roundTripped iken Double kaynakli M${"%.1f".format(raw)} bildirilmeli",
                AlertLevel.INFO,
                level("%.1f".format(raw).replace(',', '.').toDouble(), settings),
            )
        }
    }

    /**
     * Kaydiricinin ULASILABILIR TUM konumlarini tarar (1.0..6.0 arasi 51 durak).
     * Her durak icin, o esigin tam ustundeki buyuklugun bildirildigini dogrular.
     * Tek bir konumda bile float kaymasi olsa kullanici "esigi M3.3 yaptim ama
     * M3.3 depremi bildirmedi" derdi ve sebebini bulmak neredeyse imkansiz olurdu.
     */
    @Test
    fun `kaydiricinin her konumunda sinir degeri bildirilir`() {
        val basamak = 50
        for (i in 0..basamak) {
            // Compose Slider'in ic hesabiyla ayni yol: start + (end-start) * kesir
            val raw: Float = 1.0f + (6.0f - 1.0f) * (i.toFloat() / basamak)
            val esik = raw.toDouble()                       // SharedPreferences Float -> Double
            val gosterilen = "%.1f".format(raw).replace(',', '.').toDouble()  // ekranda yazan deger

            val settings = s.copy(minMag = esik, alarmMag = 9.0)
            assertEquals(
                "Kaydirici konumu $i: esik=$esik iken M$gosterilen bildirilmeli",
                AlertLevel.INFO,
                level(gosterilen, settings),
            )
            assertEquals(
                "Kaydirici konumu $i: esik=$esik iken M$esik bildirilmeli",
                AlertLevel.INFO,
                level(esik, settings),
            )
        }
    }

    /**
     * Ayni tarama alarm kaydiricisi icin (2.5..7.0, 46 durak). Bu yol sizi
     * uykudan uyandiran yol oldugu icin sinir davranisi burada daha da onemli:
     * "alarmi M4.0 kurdum ama M4.0 depremde sadece sessiz bildirim geldi"
     * durumu olusmamali.
     */
    @Test
    fun `alarm kaydiricisinin her konumunda sinir degeri alarm calar`() {
        val basamak = 45
        for (i in 0..basamak) {
            val raw: Float = 2.5f + (7.0f - 2.5f) * (i.toFloat() / basamak)
            val esik = raw.toDouble()
            val gosterilen = "%.1f".format(raw).replace(',', '.').toDouble()

            val settings = s.copy(minMag = 0.1, alarmMag = esik, alarmRadiusKm = 300.0)
            assertEquals(
                "Alarm kaydirici konumu $i: esik=$esik iken M$gosterilen alarm calmali",
                AlertLevel.ALARM,
                level(gosterilen, settings),
            )
            // Esigin bir basamak altindaki deprem alarm DEGIL, normal bildirim olmali.
            val altinda = Math.round((gosterilen - 0.1) * 10.0) / 10.0
            if (altinda >= 0.2) {
                assertEquals(
                    "Alarm kaydirici konumu $i: esik=$esik iken M$altinda alarm CALMAMALI",
                    AlertLevel.INFO,
                    level(altinda, settings),
                )
            }
        }
    }
}
