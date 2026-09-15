package com.berk.deprem.data

import com.berk.deprem.core.Geo
import com.berk.deprem.core.Settings
import com.berk.deprem.model.Report
import com.berk.deprem.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Yoklamayla calisan bir sismik veri kaynagi. */
interface PollSource {
    val source: Source
    suspend fun fetch(settings: Settings, sinceMs: Long): List<Report>
}

/**
 * Donus bos dize ise "sonuc yok" demektir, hata degil.
 *
 * EMSC FDSN sakin donemlerde 204 No Content donuyor. Bunu istisna saymak,
 * hicbir sey olmadigi surece kaynagi surekli "hatali" gostermek ve geri
 * cekilme suresini bosu bosuna buyutmek anlamina gelirdi.
 */
private fun get(url: String): String {
    val req = Request.Builder().url(url)
        .header("User-Agent", Http.UA)
        .header("Accept", "application/json, text/plain, */*")
        .build()
    Http.client.newCall(req).execute().use { resp ->
        if (resp.code == 204) return ""
        if (!resp.isSuccessful) error("HTTP ${resp.code} ${resp.message}")
        val bytes = resp.body?.bytes() ?: return ""
        val ctype = resp.header("Content-Type").orEmpty().lowercase()
        val charset = if (ctype.contains("windows-1254") || ctype.contains("iso-8859-9")) {
            java.nio.charset.Charset.forName("windows-1254")
        } else {
            Charsets.UTF_8
        }
        return String(bytes, charset)
    }
}

/**
 * EMSC FDSN-Event. WebSocket'in gozunden kacan ya da baglantinin koptugu
 * anlarda olusan bosluklari kapatan yedek kanal.
 */
object EmscSource : PollSource {
    override val source = Source.EMSC

    override suspend fun fetch(settings: Settings, sinceMs: Long): List<Report> = withContext(Dispatchers.IO) {
        val radiusDeg = (settings.radiusKm / 111.19).coerceIn(0.1, 20.0)
        val url = buildString {
            append("https://www.seismicportal.eu/fdsnws/event/1/query?format=json")
            append("&starttime=").append(Time.afadParam(sinceMs))
            append("&lat=").append(settings.homeLat)
            append("&lon=").append(settings.homeLon)
            append("&maxradius=").append(String.format(Locale.ROOT, "%.4f", radiusDeg))
            append("&minmag=").append(maxOf(0.1, settings.minMag - 0.5))
            append("&limit=200")
        }
        val body = get(url)
        if (body.isBlank()) return@withContext emptyList()
        parse(JSONObject(body))
    }

    fun parse(root: JSONObject, nowMs: Long = System.currentTimeMillis()): List<Report> {
        val feats = root.optJSONArray("features") ?: JSONArray()
        return (0 until feats.length()).mapNotNull { i ->
            runCatching { parseEmscFeature(feats.getJSONObject(i), nowMs) }.getOrNull()
        }
    }
}

/** WebSocket ve FDSN ayni "feature" semasini kullanir. */
fun parseEmscFeature(feat: JSONObject, nowMs: Long = System.currentTimeMillis()): Report {
    val p = feat.getJSONObject("properties")
    return Report(
        source = Source.EMSC,
        sourceEventId = p.optString("unid").ifBlank { feat.optString("id") },
        originTimeMs = Time.parseIso(p.getString("time")),
        lat = p.getDouble("lat"),
        lon = p.getDouble("lon"),
        depthKm = p.optDouble("depth", 10.0),
        mag = p.getDouble("mag"),
        magType = p.optString("magtype", "M").uppercase(),
        region = p.optString("flynn_region", ""),
        receivedAtMs = nowMs,
        // EMSC her guncellemede lastupdate'i ilerletir; dakikaya yuvarlayip revizyon sayaci yapiyoruz.
        revision = runCatching { (Time.parseIso(p.getString("lastupdate")) / 60000).toInt() }.getOrDefault(0),
    )
}

/**
 * AFAD apiv2. Birincil servisnet adresi ve yedek deprem.afad.gov.tr adresi
 * arasinda otomatik gecis yaparak kesintisiz calisir.
 */
object AfadSource : PollSource {
    override val source = Source.AFAD
    private const val PRIMARY_BASE = "https://servisnet.afad.gov.tr/apigateway/deprem/apiv2/event/filter"
    private const val BACKUP_BASE = "https://deprem.afad.gov.tr/apiv2/event/filter"

    override suspend fun fetch(settings: Settings, sinceMs: Long): List<Report> = withContext(Dispatchers.IO) {
        val dLat = (settings.radiusKm / 111.19).coerceIn(0.1, 50.0)
        val cosLat = Math.cos(Math.toRadians(settings.homeLat)).coerceAtLeast(0.2)
        val dLon = (settings.radiusKm / (111.19 * cosLat)).coerceIn(0.1, 80.0)
        val minLat = (settings.homeLat - dLat).coerceIn(-90.0, 90.0)
        val maxLat = (settings.homeLat + dLat).coerceIn(-90.0, 90.0)
        val minLon = (settings.homeLon - dLon).coerceIn(-180.0, 180.0)
        val maxLon = (settings.homeLon + dLon).coerceIn(-180.0, 180.0)

        fun buildUrl(base: String) = buildString {
            append(base)
            append("?start=").append(Time.afadParam(sinceMs))
            // Sunucu saati bizimkinden birkac saniye ileri olabilir; pencereyi ileri tarafa tasiyoruz.
            append("&end=").append(Time.afadParam(System.currentTimeMillis() + 600_000))
            append("&minlat=").append(String.format(Locale.ROOT, "%.3f", minLat))
            append("&maxlat=").append(String.format(Locale.ROOT, "%.3f", maxLat))
            append("&minlon=").append(String.format(Locale.ROOT, "%.3f", minLon))
            append("&maxlon=").append(String.format(Locale.ROOT, "%.3f", maxLon))
            append("&orderby=timedesc&limit=200")
        }

        val body = runCatching { get(buildUrl(PRIMARY_BASE)) }
            .recoverCatching { get(buildUrl(BACKUP_BASE)) }
            .getOrThrow()

        if (body.isBlank()) return@withContext emptyList()
        parse(JSONArray(body))
    }

    /** Ag katmanindan ayri: ayristirma mantigi boylece test edilebiliyor. */
    fun parse(arr: JSONArray, nowMs: Long = System.currentTimeMillis()): List<Report> =
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val e = arr.getJSONObject(i)
                val lat = e.optDouble("latitude", e.optString("latitude", "0.0").toDoubleOrNull() ?: 0.0)
                val lon = e.optDouble("longitude", e.optString("longitude", "0.0").toDoubleOrNull() ?: 0.0)
                val depth = e.optDouble("depth", e.optString("depth", "10.0").toDoubleOrNull() ?: 10.0)
                val mag = e.optDouble("magnitude", e.optString("magnitude", "0.0").toDoubleOrNull() ?: 0.0)
                Report(
                    source = Source.AFAD,
                    sourceEventId = e.getString("eventID"),
                    originTimeMs = Time.parseUtcNoZone(e.getString("date")),
                    lat = lat,
                    lon = lon,
                    depthKm = depth,
                    mag = mag,
                    magType = e.optString("type", "ML").uppercase(),
                    region = e.optString("location", ""),
                    receivedAtMs = nowMs,
                    revision = if (e.optBoolean("isEventUpdate", false)) 1 else 0,
                )
            }.getOrNull()
        }
}

/**
 * Kandilli (KOERI). Hizli yanit veren JSON ayna uzerinden calisir;
 * ayna yanit vermezse otomatik olarak Kandilli Rasathanesi resmi web sitesine (lst0.asp)
 * baglanip ham veriyi ayristirarak kesintiyi onler.
 */
object KoeriSource : PollSource {
    override val source = Source.KOERI
    private const val MIRROR_URL = "https://api.orhanaydogdu.com.tr/deprem/kandilli/live?limit=100"
    private const val OFFICIAL_URL = "http://www.koeri.boun.edu.tr/scripts/lst0.asp"

    override suspend fun fetch(settings: Settings, sinceMs: Long): List<Report> = withContext(Dispatchers.IO) {
        val mirrorResult = runCatching {
            val body = get(MIRROR_URL)
            if (body.isBlank()) emptyList() else parse(JSONObject(body), settings, sinceMs)
        }
        if (mirrorResult.isSuccess) {
            return@withContext mirrorResult.getOrThrow()
        }

        // Ayna basarisiz olursa resmi Kandilli Rasathanesi sayfasina yedek olarak baglan
        val officialBody = get(OFFICIAL_URL)
        if (officialBody.isBlank()) return@withContext emptyList()
        parseOfficialText(officialBody, settings, sinceMs)
    }

    fun parse(
        root: JSONObject,
        settings: Settings,
        sinceMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Report> {
        val arr = root.optJSONArray("result") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val e = arr.getJSONObject(i)
                val coords = e.getJSONObject("geojson").getJSONArray("coordinates")
                val lon = coords.getDouble(0)
                val lat = coords.getDouble(1)
                // Ayna "date_time" alanini Europe/Istanbul yerel saatiyle verir;
                // "created_at" ise dogrudan UTC epoch. Belirsizlik birakmamak icin onu kullaniyoruz.
                val originMs = e.getLong("created_at") * 1000
                if (originMs < sinceMs) return@runCatching null
                if (Geo.distanceKm(settings.homeLat, settings.homeLon, lat, lon) > settings.radiusKm) {
                    return@runCatching null
                }
                Report(
                    source = Source.KOERI,
                    sourceEventId = e.getString("earthquake_id"),
                    originTimeMs = originMs,
                    lat = lat,
                    lon = lon,
                    depthKm = e.optDouble("depth", 10.0),
                    mag = e.optDouble("mag", 0.0),
                    magType = "ML",
                    region = e.optString("title", ""),
                    receivedAtMs = nowMs,
                    revision = e.optInt("rev", 0),
                )
            }.getOrNull()
        }
    }

    private val OFFICIAL_LINE_REGEX = Regex("""(\d{4}\.\d{2}\.\d{2})\s+(\d{2}:\d{2}:\d{2})\s+([\d\.]+)\s+([\d\.]+)\s+([\d\.]+)\s+([-\.\d]+)\s+([-\.\d]+)\s+([-\.\d]+)\s+(.*?)\s+(İlksel|Revize|\d{4}\.\d{2})""")

    /**
     * Kandilli Rasathanesi lst0.asp ham metin sayfasini ayristirir.
     */
    fun parseOfficialText(
        rawText: String,
        settings: Settings,
        sinceMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<Report> {
        val pre = rawText.substringAfter("<pre>", "").substringBefore("</pre>", "")
        val content = if (pre.isNotBlank()) pre else rawText
        return content.lineSequence().mapNotNull { line ->
            runCatching {
                val m = OFFICIAL_LINE_REGEX.find(line) ?: return@runCatching null
                val (dateStr, timeStr, latStr, lonStr, depthStr, mdStr, mlStr, mwStr, placeStr) = m.destructured
                val originMs = Time.parseKoeriLocal("$dateStr $timeStr")
                if (originMs < sinceMs) return@runCatching null

                val lat = latStr.toDouble()
                val lon = lonStr.toDouble()
                if (Geo.distanceKm(settings.homeLat, settings.homeLon, lat, lon) > settings.radiusKm) {
                    return@runCatching null
                }

                val mag = mlStr.toDoubleOrNull()
                    ?: mwStr.toDoubleOrNull()
                    ?: mdStr.toDoubleOrNull()
                    ?: 0.0

                val eventId = "koeri-${originMs / 1000}-${String.format(Locale.ROOT, "%.2f", lat)}-${String.format(Locale.ROOT, "%.2f", lon)}"

                Report(
                    source = Source.KOERI,
                    sourceEventId = eventId,
                    originTimeMs = originMs,
                    lat = lat,
                    lon = lon,
                    depthKm = depthStr.toDoubleOrNull() ?: 10.0,
                    mag = mag,
                    magType = if (mlStr.toDoubleOrNull() != null) "ML" else if (mwStr.toDoubleOrNull() != null) "MW" else "MD",
                    region = placeStr.trim(),
                    receivedAtMs = nowMs,
                    revision = if (line.contains("Revize", true)) 1 else 0,
                )
            }.getOrNull()
        }.toList()
    }
}
