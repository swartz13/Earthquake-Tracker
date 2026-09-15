package com.berk.deprem

import com.berk.deprem.core.Ingest
import com.berk.deprem.core.QuakeStore
import com.berk.deprem.data.AfadSource
import com.berk.deprem.data.EmscSource
import com.berk.deprem.model.Report
import com.berk.deprem.model.Source
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import com.berk.deprem.core.AlertPolicy
import com.berk.deprem.core.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eslestirme motorunun gercek veri uzerindeki davranisi.
 *
 * Sabit dosyalar 20 Agustos 2026 aksami Marmara'da olan bir artci serisini
 * icerir: ayni episantrda saniyeler-dakikalar arayla ard arda depremler.
 * Bu, hem "ayni olayi birlestir" hem de "ayri olaylari birlestirme"
 * kurallarini ayni anda zorlayan en zor senaryo.
 */
class MatchingTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    private fun emsc() = EmscSource.parse(JSONObject(fixture("emsc.json")), nowMs = 0L)
    private fun afad() = AfadSource.parse(JSONArray(fixture("afad.json")), nowMs = 0L)

    @Test
    fun `iki kaynagin ayni depremleri tek olayda birlesir`() = runTest {
        val store = QuakeStore(nowMs = { 1787270000000L })
        emsc().forEach { store.ingest(it) }
        afad().forEach { store.ingest(it) }

        val merged = store.quakes.value
        // EMSC 7, AFAD 8 kayit verdi; bunlarin 7'si ayni depremler.
        // AFAD'in fazladan bildirdigi 21:47:04 olayi EMSC'de yok.
        assertEquals(8, merged.size)
        assertEquals(7, merged.count { it.confirmCount == 2 })
        assertEquals(1, merged.count { it.confirmCount == 1 })
    }

    /**
     * Serideki 21:47:04 ve 21:47:48 depremleri ayni noktada ama 44 saniye arayla.
     * Gevsek bir zaman penceresi bunlari tek olay yapardi ve kullanici ikinci
     * sarsintiyi hic gormezdi.
     */
    @Test
    fun `saniyeler arayla olan ayri depremler birlestirilmez`() = runTest {
        val store = QuakeStore(nowMs = { 1787270000000L })
        afad().forEach { store.ingest(it) }

        val n = afad().size
        assertEquals("AFAD'in $n kaydi $n ayri olay olmali", n, store.quakes.value.size)
    }

    @Test
    fun `ayni kaynagin buyukluk revizyonu yeni olay yaratmaz`() = runTest {
        val store = QuakeStore(nowMs = { 1_000_000L })
        val first = report(mag = 3.4)
        assertTrue(store.ingest(first) is Ingest.New)

        val revised = first.copy(mag = 4.1, revision = 1, receivedAtMs = first.receivedAtMs + 60_000)
        val result = store.ingest(revised)

        assertTrue("Revizyon Revised olarak isaretlenmeli, gelen: $result", result is Ingest.Revised)
        assertEquals(3.4, (result as Ingest.Revised).previousMag, 0.001)
        assertEquals(1, store.quakes.value.size)
        assertEquals(4.1, store.quakes.value.first().mag, 0.001)
    }

    @Test
    fun `anlamsiz tekrar yok sayilir`() = runTest {
        val store = QuakeStore(nowMs = { 1_000_000L })
        val r = report(mag = 3.4)
        store.ingest(r)
        assertTrue(store.ingest(r.copy(receivedAtMs = r.receivedAtMs + 5000)) is Ingest.Ignored)
        assertEquals(1, store.quakes.value.size)
    }

    @Test
    fun `ikinci kaynak dogrulama olarak eklenir ve yerel ag tercih edilir`() = runTest {
        val store = QuakeStore(nowMs = { 1_000_000L })
        // EMSC once haber veriyor (WebSocket), AFAD 40 sn sonra dogruluyor.
        val emscReport = report(source = Source.EMSC, mag = 4.3, lat = 40.80, lon = 29.05)
        store.ingest(emscReport)

        val afadReport = report(
            source = Source.AFAD, mag = 4.0, lat = 40.79, lon = 29.06,
            originTimeMs = emscReport.originTimeMs + 2000,
        ).copy(receivedAtMs = emscReport.receivedAtMs + 40_000)

        val result = store.ingest(afadReport)
        assertTrue(result is Ingest.Confirmed)

        val q = store.quakes.value.single()
        assertEquals(2, q.confirmCount)
        // Gosterimde AFAD cozumu esas alinir (yerel ag onceligi).
        assertEquals(Source.AFAD, q.best.source)
        assertEquals(4.0, q.mag, 0.001)
        // Ilk haber veren EMSC olarak kalir.
        assertEquals(Source.EMSC, q.fastest.source)
        assertEquals(0.3, q.magSpread, 0.001)
    }

    @Test
    fun `uzak depremler ayri olaylar olarak kalir`() = runTest {
        val store = QuakeStore(nowMs = { 1_000_000L })
        store.ingest(report(source = Source.EMSC, lat = 40.8, lon = 29.0))
        // Ayni anda, 300 km oteden baska bir kaynak: farkli deprem.
        store.ingest(report(source = Source.AFAD, lat = 38.4, lon = 27.1))
        assertEquals(2, store.quakes.value.size)
    }

    private var idSeq = 0
    private fun report(
        source: Source = Source.AFAD,
        mag: Double = 3.0,
        lat: Double = 40.78,
        lon: Double = 29.07,
        originTimeMs: Long = 900_000L,
    ) = Report(
        source = source,
        sourceEventId = "ev${idSeq++}",
        originTimeMs = originTimeMs,
        lat = lat,
        lon = lon,
        depthKm = 10.0,
        mag = mag,
        magType = "ML",
        region = "Test",
        receivedAtMs = originTimeMs + 20_000,
    )

    /**
     * EMSC WebSocket'i dunya genelini yayinlar. Suzgec olmayinca Endonezya
     * depremleri Istanbul listesine giriyordu — cihazda 10799 km uzaklikta
     * kayitlar goruldu.
     */
    @Test
    fun `yaricap disindaki olaylar ilgisiz sayilir`() {
        val s = Settings(homeLat = 41.0082, homeLon = 28.9784, radiusKm = 300.0)

        // Marmara — iceride
        assertTrue(AlertPolicy.isRelevant(40.78, 29.07, s))
        // Simav, Kutahya (~200 km) — iceride
        assertTrue(AlertPolicy.isRelevant(39.23, 29.04, s))
        // Sirbistan (~740 km) — disarida
        assertFalse(AlertPolicy.isRelevant(43.9, 20.5, s))
        // Flores, Endonezya (~10800 km) — disarida
        assertFalse(AlertPolicy.isRelevant(-8.5, 122.0, s))
    }
}
