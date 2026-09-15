package com.berk.deprem

import com.berk.deprem.core.Geo
import com.berk.deprem.core.Settings
import com.berk.deprem.data.AfadSource
import com.berk.deprem.data.EmscSource
import com.berk.deprem.data.KoeriSource
import com.berk.deprem.data.Time
import com.berk.deprem.model.Source
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Uc kaynaktan canli olarak yakalanmis gercek yanitlar uzerinde ayristirma testleri.
 * Sabit dosyalar: app/src/test/resources/{emsc,afad,koeri}.json
 */
class ParsingTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    private val settings = Settings(radiusKm = 5000.0)

    @Test
    fun `EMSC feature'lari ayristirilir`() {
        val reports = EmscSource.parse(JSONObject(fixture("emsc.json")), nowMs = 0L)
        assertEquals(7, reports.size)
        assertTrue(reports.all { it.source == Source.EMSC })
        assertTrue(reports.all { it.mag > 0 })
        assertTrue(reports.all { it.lat in 40.0..42.0 })
    }

    @Test
    fun `AFAD kayitlari ayristirilir`() {
        val reports = AfadSource.parse(JSONArray(fixture("afad.json")), nowMs = 0L)
        assertTrue(reports.size >= 5)
        assertTrue(reports.all { it.source == Source.AFAD })
        assertTrue(reports.all { it.region.isNotBlank() })
    }

    @Test
    fun `Kandilli kayitlari ayristirilir`() {
        val reports = KoeriSource.parse(JSONObject(fixture("koeri.json")), settings, sinceMs = 0L, nowMs = 0L)
        assertTrue(reports.size >= 50)
        assertTrue(reports.all { it.source == Source.KOERI })
    }

    /**
     * Projedeki en sinsi hata kaynagi: AFAD web arayuzu yerel saat gosterir,
     * apiv2 ise UTC dondurur. Yanlis varsayim 3 saatlik kayma yapar ve
     * eslestirmeyi tamamen bozar.
     *
     * Kontrol: her EMSC olayina zamanca en yakin AFAD kaydini bul; ayni olaylar
     * ise episantrlar da cakismak zorunda. 3 saatlik kayma olsaydi hicbir cift
     * hem zaman hem konum testini gecemezdi.
     */
    @Test
    fun `AFAD ve EMSC zaman damgalari ayni dilimde`() {
        val emsc = EmscSource.parse(JSONObject(fixture("emsc.json")), nowMs = 0L)
        val afad = AfadSource.parse(JSONArray(fixture("afad.json")), nowMs = 0L)

        var matched = 0
        for (e in emsc) {
            val near = afad.minByOrNull { abs(it.originTimeMs - e.originTimeMs) } ?: continue
            val deltaSec = abs(near.originTimeMs - e.originTimeMs) / 1000
            if (deltaSec > 5) continue
            val km = Geo.distanceKm(e.lat, e.lon, near.lat, near.lon)
            assertTrue(
                "Zamanca ortusen cift $km km ayri — dilim varsayimi bozulmus olabilir",
                km < 15.0
            )
            matched++
        }
        assertTrue("Karsilastirilacak ortak olay bulunamadi ($matched)", matched >= 5)
    }

    @Test
    fun `zaman ayristirma bicimleri`() {
        // AFAD: dilim soneki yok, UTC kabul ediliyor.
        assertEquals(
            Time.parseIso("2026-08-20T21:59:21Z"),
            Time.parseUtcNoZone("2026-08-20T21:59:21")
        )
        // EMSC: degisken uzunlukta saniye kesirleri.
        assertEquals(
            1787263161900L,
            Time.parseIso("2026-08-20T21:59:21.9Z")
        )
    }

    /** Bos FeatureCollection / bos dizi cokmemeli, bos liste vermeli. */
    @Test
    fun `bos yanitlar bos liste dondurur`() {
        assertEquals(0, EmscSource.parse(JSONObject("""{"type":"FeatureCollection","features":[]}"""), 0L).size)
        assertEquals(0, EmscSource.parse(JSONObject("{}"), 0L).size)
        assertEquals(0, AfadSource.parse(JSONArray("[]"), 0L).size)
        assertEquals(0, KoeriSource.parse(JSONObject("""{"result":[]}"""), settings, 0L, 0L).size)
    }

    /** Bozuk tek kayit tum yoklamayi dusurmemeli. */
    @Test
    fun `bozuk kayit atlanir digerleri okunur`() {
        val arr = JSONArray("""[
            {"eventID":"1","date":"2026-08-20T21:59:21","latitude":"40.78","longitude":"29.07","depth":"11","magnitude":"2.4","type":"ML","location":"Marmara"},
            {"eventID":"2","date":"BOZUK","latitude":"x","longitude":"29.07","depth":"11","magnitude":"2.4"},
            {"eventID":"3","date":"2026-08-20T22:01:00","latitude":"40.79","longitude":"29.08","depth":"9","magnitude":"3.1","type":"ML","location":"Marmara"}
        ]""")
        val reports = AfadSource.parse(arr, nowMs = 0L)
        assertEquals(2, reports.size)
        assertEquals(listOf("1", "3"), reports.map { it.sourceEventId })
    }

    @Test
    fun `AFAD bosluklu ve T li tarihleri dogru ayristirir`() {
        val tMs = Time.parseUtcNoZone("2026-09-14T10:00:00")
        val spaceMs = Time.parseUtcNoZone("2026-09-14 10:00:00")
        assertEquals(tMs, spaceMs)
    }

    @Test
    fun `Kandilli resmi sitesi lst0 asp metni ayristirilir`() {
        val raw = """
<pre>
2026.09.14 13:44:36  38.7025   26.5527        2.8      -.-  1.7  -.-   IZMIR KORFEZI (EGE DENIZI)                        İlksel
2026.09.14 13:28:22  40.3613   27.0808       24.2      -.-  1.4  -.-   AYITDERE-BIGA (CANAKKALE)                         İlksel
</pre>
        """.trimIndent()
        val reports = KoeriSource.parseOfficialText(raw, settings, sinceMs = 0L, nowMs = 0L)
        assertEquals(2, reports.size)
        assertEquals(Source.KOERI, reports[0].source)
        assertEquals(1.7, reports[0].mag, 0.01)
        assertEquals(38.7025, reports[0].lat, 0.0001)
        assertEquals(26.5527, reports[0].lon, 0.0001)
        assertEquals("IZMIR KORFEZI (EGE DENIZI)", reports[0].region)
        assertEquals(1.4, reports[1].mag, 0.01)
    }
}
