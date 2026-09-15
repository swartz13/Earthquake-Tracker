package com.berk.deprem.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Kullanicinin referans noktasi: sabit Istanbul merkezi ya da cihazin son konumu. */
data class Settings(
    val language: String = "tr",
    val homeLat: Double = 41.0082,
    val homeLon: Double = 28.9784,
    val useDeviceLocation: Boolean = false,
    /**
     * Cihazdan gercekten konum alinabildi mi. "Konumumu kullan" acik olsa bile
     * henuz bir konum tespiti gelmemis olabilir; o durumda Istanbul merkezi
     * kullanilmaya devam eder ve arayuz bunu dogru sekilde soylemelidir.
     */
    val homeFromDevice: Boolean = false,
    /** Bu yaricapin disindaki depremler tamamen yok sayilir. */
    val radiusKm: Double = 300.0,
    /** Bildirim esigi: bunun altindakiler icin telefon hic uyarmaz. */
    val minMag: Double = 2.5,
    /**
     * Listeleme esigi. Bildirim esiginden ayri tutuluyor: cogu zaman
     * "bildirim gelmesin ama olan biteni gorebileyim" isteniyor.
     * 0 = yaricap icindeki her seyi goster.
     */
    val listMinMag: Double = 0.0,
    /** Bu buyuklugun ustu = tam ekran, sesli alarm (DND'yi deler). */
    val alarmMag: Double = 4.0,
    /** Alarm sadece bu mesafenin icinde calar. */
    val alarmRadiusKm: Double = 150.0,
    val monitorEnabled: Boolean = true,
    val emscEnabled: Boolean = true,
    val afadEnabled: Boolean = true,
    val koeriEnabled: Boolean = true,
    /** Saniye cinsinden yoklama araliklari. WebSocket olmayan kaynaklar icin. */
    val afadPollSec: Int = 15,
    val koeriPollSec: Int = 25,
    val emscPollSec: Int = 90,
)

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("deprem_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state

    val current: Settings get() = _state.value

    private fun load() = Settings(
        language = sp.getString("language", "tr") ?: "tr",
        homeLat = sp.getFloat("homeLat", 41.0082f).toDouble(),
        homeLon = sp.getFloat("homeLon", 28.9784f).toDouble(),
        useDeviceLocation = sp.getBoolean("useDeviceLocation", false),
        homeFromDevice = sp.getBoolean("homeFromDevice", false),
        radiusKm = sp.getFloat("radiusKm", 300f).toDouble(),
        minMag = sp.getFloat("minMag", 2.5f).toDouble(),
        listMinMag = sp.getFloat("listMinMag", 0f).toDouble(),
        alarmMag = sp.getFloat("alarmMag", 4.0f).toDouble(),
        alarmRadiusKm = sp.getFloat("alarmRadiusKm", 150f).toDouble(),
        monitorEnabled = sp.getBoolean("monitorEnabled", true),
        emscEnabled = sp.getBoolean("emscEnabled", true),
        afadEnabled = sp.getBoolean("afadEnabled", true),
        koeriEnabled = sp.getBoolean("koeriEnabled", true),
        afadPollSec = sp.getInt("afadPollSec", 15),
        koeriPollSec = sp.getInt("koeriPollSec", 25),
        emscPollSec = sp.getInt("emscPollSec", 90),
    )

    /**
     * Buyukluk esiklerini 0.1 basamagina yuvarlar.
     *
     * Kaydiricidan gelen ve SharedPreferences'ta Float olarak saklanan deger
     * 3.3 yerine 3.3000002 olabiliyor. Bu, tam M3.3'luk bir depremin esigin
     * ALTINDA sayilmasina yol aciyordu — ekranda yine "M3,3" yazdigi icin
     * sebebi gorunmuyordu.
     */
    private fun snap(v: Double) = Math.round(v * 10.0) / 10.0

    fun update(block: (Settings) -> Settings) {
        val raw = block(_state.value)
        val s = raw.copy(
            minMag = snap(raw.minMag),
            alarmMag = snap(raw.alarmMag),
            listMinMag = snap(raw.listMinMag),
        )
        sp.edit().apply {
            putString("language", s.language)
            putFloat("homeLat", s.homeLat.toFloat())
            putFloat("homeLon", s.homeLon.toFloat())
            putBoolean("useDeviceLocation", s.useDeviceLocation)
            putBoolean("homeFromDevice", s.homeFromDevice)
            putFloat("radiusKm", s.radiusKm.toFloat())
            putFloat("minMag", s.minMag.toFloat())
            putFloat("listMinMag", s.listMinMag.toFloat())
            putFloat("alarmMag", s.alarmMag.toFloat())
            putFloat("alarmRadiusKm", s.alarmRadiusKm.toFloat())
            putBoolean("monitorEnabled", s.monitorEnabled)
            putBoolean("emscEnabled", s.emscEnabled)
            putBoolean("afadEnabled", s.afadEnabled)
            putBoolean("koeriEnabled", s.koeriEnabled)
            putInt("afadPollSec", s.afadPollSec)
            putInt("koeriPollSec", s.koeriPollSec)
            putInt("emscPollSec", s.emscPollSec)
        }.apply()
        _state.value = s
    }
}
