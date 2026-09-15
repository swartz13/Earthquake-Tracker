package com.berk.deprem

import com.berk.deprem.core.Places
import com.berk.deprem.core.Turkish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Yerlesim cozumlemesi, EMSC'nin kendi sitesinde yayinladigi degerlerle
 * karsilastirilarak dogrulanir. Boylece "WESTERN TURKEY" yerine yazdigimiz
 * metnin dogrulugu tahmine degil, dis bir referansa dayanir.
 */
class PlacesTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun yukle() {
            Places.load(File("src/main/assets/yerlesimler.tsv").inputStream())
        }
    }

    @Test
    fun `veri kumesi yuklendi`() {
        assertTrue("yerlesim sayisi beklenenden az: ${Places.size}", Places.size > 10_000)
    }

    /**
     * EMSC kaydi (20 Agustos 2026 23:47:09 UTC, M2.1):
     *   Location  39.239 ; 29.012
     *   17 km N of Simav, Türkiye / pop: 34,900
     *   72 km NW of Uşak, Türkiye / pop: 152,000
     */
    @Test
    fun `EMSC ornegiyle birebir ayni yerlesimler bulunur`() {
        val info = Places.describe(39.239, 29.012)

        assertEquals("Simav", info.nearest?.name)
        assertEquals("Kütahya", info.nearest?.province)
        assertEquals(17.0, info.nearestKm, 1.0)      // EMSC: 17 km

        assertEquals("Uşak", info.city?.name)
        assertEquals(72.0, info.cityKm, 2.0)          // EMSC: 72 km

        // EMSC "17 km N of Simav" diyor; biz de ayni yonu ayni referanstan veriyoruz.
        assertEquals("Simav'ın 17 km kuzeyinde", info.headline)
        assertEquals("Simav'ın 17 km kuzeyinde", info.localizedHeadline(com.berk.deprem.ui.TrStrings))
        assertEquals("17 km north of Simav", info.localizedHeadline(com.berk.deprem.ui.EnStrings))
    }

    @Test
    fun `deniz icindeki episantr icin de en yakin kiyi yerlesimi bulunur`() {
        // Marmara Denizi, Adalar acigi — AFAD'in "Marmara Denizi" dedigi bolge.
        val info = Places.describe(40.78, 29.07)
        assertNotNull(info.nearest)
        assertTrue("kiyiya makul uzaklikta olmali: ${info.nearestKm}", info.nearestKm < 30.0)
        assertTrue(info.headline.contains("km"))
        assertTrue(info.localizedHeadline(com.berk.deprem.ui.EnStrings).contains("km"))
    }

    @Test
    fun `yerlesim merkezindeki deprem yon yerine merkez der`() {
        val info = Places.describe(39.0875, 28.9767) // Simav merkezi
        assertTrue(info.headline, info.headline.contains("merkezinde"))
        assertEquals("Simav (Kütahya) merkezinde", info.localizedHeadline(com.berk.deprem.ui.TrStrings))
        assertEquals("Center of Simav (Kütahya)", info.localizedHeadline(com.berk.deprem.ui.EnStrings))
    }

    @Test
    fun `tamlayan eki unlu uyumuna uyar`() {
        assertEquals("Simav'ın", Turkish.genitive("Simav"))
        assertEquals("Uşak'ın", Turkish.genitive("Uşak"))
        assertEquals("İzmir'in", Turkish.genitive("İzmir"))
        assertEquals("Çorum'un", Turkish.genitive("Çorum"))
        assertEquals("Gölcük'ün", Turkish.genitive("Gölcük"))
        // Unluyle bitenlerde kaynastirma 'n'si
        assertEquals("Kütahya'nın", Turkish.genitive("Kütahya"))
        assertEquals("Bolu'nun", Turkish.genitive("Bolu"))
        assertEquals("Düzce'nin", Turkish.genitive("Düzce"))
    }

    @Test
    fun `yonler dogru hesaplanir`() {
        // Simav (39.0875, 28.9767) -> kuzeyindeki bir nokta
        assertEquals("kuzeyinde", Turkish.direction(39.0875, 28.9767, 39.30, 28.9767))
        assertEquals("güneyinde", Turkish.direction(39.0875, 28.9767, 38.90, 28.9767))
        assertEquals("doğusunda", Turkish.direction(39.0875, 28.9767, 39.0875, 29.30))
        assertEquals("batısında", Turkish.direction(39.0875, 28.9767, 39.0875, 28.60))
    }
}
