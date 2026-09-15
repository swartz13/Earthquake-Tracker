package com.berk.deprem.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.berk.deprem.model.Source
import java.util.Locale
import kotlin.math.roundToInt

interface AppStrings {
    val appName: String
    val monitoringActive: String
    val monitoringStopped: String
    val fixed: String
    val deviceLocation: String
    val waitingLocation: String
    val locationUnavailable: String
    val referencePrefix: String
    val referenceIstanbul: String
    fun locationStatusLabel(status: com.berk.deprem.core.LocationStatus): String
    val kmRadius: String
    val tapToRefresh: String
    val settings: String

    // Status / Chips
    val live: String
    val waiting: String
    val noNetwork: String
    val error: String
    fun secondsAgo(s: Long): String
    fun firstReportsCount(count: Int): String

    // Detail Dialog
    fun sourceDialogTitle(source: Source): String
    val connectionLiveWs: String
    val connectionNetworkError: String
    val connectionError: String
    fun connectionHealthy(sec: Long): String
    val connectionWaitingFirst: String
    fun lastSuccessAgo(sec: Long): String
    fun eventsObservedCount(count: Int): String
    fun firstReportsDetail(count: Int): String
    val errorDetails: String
    val close: String
    val refreshNow: String

    // Warnings / Empty
    val serversUnreachable: String
    val noInternet: String
    val dnsWarning: String
    val noInternetWarning: String
    val batteryWarningTitle: String
    val batteryWarningBody: String
    val noQuakesYet: String
    val emptyRunningSubtitle: String
    val emptyStoppedSubtitle: String
    fun hiddenRecordsNotice(count: Int, mag: String): String

    // Quake card
    fun directionFrom(place: String, km: Int, direction: String): String
    fun inCenterOf(place: String): String
    val unknownRegion: String
    fun distanceAndDepth(distKm: Int, depthKm: Int): String
    fun timeAgo(seconds: Long): String
    val sourceSolutions: String
    val backfill: String
    fun nearestCity(name: String, km: Int): String
    fun distanceToYou(km: Int, direction: String): String
    fun sourceRegionName(region: String): String
    fun firstReportedBy(source: String, min: Long): String
    fun intensityEstimate(roman: String): String
    fun intensityLabel(mmi: Double): String
    fun compassBearing(deg: Double): String
    fun magnitudeDiscrepancy(spread: String): String
    val beforeMonitoringLatencyNotMeasured: String
    fun estimatedShakingLine(intensityLabel: String, roman: String): String
    val emscSourceHint: String
    val afadSourceHint: String
    val koeriSourceHint: String

    // Settings
    val settingsTitle: String
    val languageSection: String
    val turkish: String
    val english: String
    val scopeSection: String
    val monitoringRadius: String
    val notificationThreshold: String
    val notificationThresholdHint: String
    val listingThreshold: String
    val allThreshold: String
    val listingThresholdHint: String
    val locationSection: String
    val useDeviceLocation: String
    val useDeviceLocationHint: String
    val sourcesSection: String
    val sourcesSectionHint: String
    val emergencyAlarmSection: String
    val emergencyAlarmHint: String
    val alarmMagnitude: String
    val alarmRadius: String
    val testAlarmButton: String
    val testNotificationRegion: String

    // Service & Notifications
    val serviceNotificationActive: String
    val serviceNotificationEmscLive: String
    val serviceNotificationOffline: String
    val serviceNotificationConnecting: String
    val quakeAlertTitle: String
    val quakeAlarmTitle: String
}

object TrStrings : AppStrings {
    override val appName = "Deprem Takip"
    override val monitoringActive = "İzleme aktif"
    override val monitoringStopped = "İzleme kapalı"
    override val fixed = "sabit"
    override val deviceLocation = "cihaz konumu"
    override val waitingLocation = "konum bekleniyor"
    override val locationUnavailable = "konum alınamadı"
    override val referencePrefix = "Referans: "
    override val referenceIstanbul = "İstanbul"
    override fun locationStatusLabel(status: com.berk.deprem.core.LocationStatus) = when (status) {
        com.berk.deprem.core.LocationStatus.ARANIYOR -> "konum aranıyor"
        com.berk.deprem.core.LocationStatus.IZIN_YOK -> "konum izni verilmedi"
        com.berk.deprem.core.LocationStatus.SERVIS_KAPALI -> "cihazda konum kapalı"
        com.berk.deprem.core.LocationStatus.BULUNAMADI -> "konum alınamadı"
    }
    override val kmRadius = "km yarıçap"
    override val tapToRefresh = "Yenilemek için dokunun · Detay için kutulara basın"
    override val settings = "Ayarlar"

    override val live = "canlı"
    override val waiting = "bekliyor"
    override val noNetwork = "ağ yok"
    override val error = "hata"
    override fun secondsAgo(s: Long) = "$s sn"
    override fun firstReportsCount(count: Int) = "$count× ilk"

    override fun sourceDialogTitle(source: Source) = "${source.short} (${source.name})"
    override val connectionLiveWs = "Bağlantı: Canlı WebSocket açık"
    override val connectionNetworkError = "Bağlantı: Ağ hatası (cihaz internete erişemiyor)"
    override val connectionError = "Bağlantı: Hata oluştu"
    override fun connectionHealthy(sec: Long) = "Bağlantı: Sağlıklı (${sec} sn önce)"
    override val connectionWaitingFirst = "Bağlantı: İlk yoklama bekleniyor..."
    override fun lastSuccessAgo(sec: Long) = "Son Başarılı Yanıt: $sec sn önce"
    override fun eventsObservedCount(count: Int) = "Görülen Olay: $count adet"
    override fun firstReportsDetail(count: Int) = "İlk Bildiren Olma: $count kez"
    override val errorDetails = "Hata Detayı:"
    override val close = "Kapat"
    override val refreshNow = "Şimdi Yenile"

    override val serversUnreachable = "Sunuculara ulaşılamıyor"
    override val noInternet = "İnternet bağlantısı yok"
    override val dnsWarning = "Telefon bağlı görünüyor ama adresler çözümlenemiyor (DNS). VPN açıp kapatıldıktan sonra sık görülür. Uçak modunu bir açıp kapatmak ya da wifi'ye geçmek genelde düzeltir. Yeniden denemek için dokunun."
    override val noInternetWarning = "Deprem kaynakları sorgulanamıyor. Sorun uygulamada değil, telefonun bağlantısında. Bağlantı gelir gelmez izleme kendiliğinden devam eder — beklemek istemezseniz dokunup hemen deneyin."
    override val batteryWarningTitle = "Pil optimizasyonu açık"
    override val batteryWarningBody = "Sistem izleme servisini uyutabilir ve bildirimler gecikir. Kapatmak için dokunun."
    override val noQuakesYet = "Henüz kayıt yok"
    override val emptyRunningSubtitle = "Belirlediğiniz yarıçap ve büyüklük eşiğine uyan deprem geldiğinde burada listelenir."
    override val emptyStoppedSubtitle = "Üstteki anahtarı açarak izlemeyi başlatın."
    override fun hiddenRecordsNotice(count: Int, mag: String) = "$count kayıt listeleme eşiğinin (M$mag) altında gizlendi"

    override fun directionFrom(place: String, km: Int, direction: String): String {
        return "${com.berk.deprem.core.Turkish.genitive(place)} $km km $direction"
    }
    override fun inCenterOf(place: String) = "$place merkezinde"
    override val unknownRegion = "Bilinmeyen bölge"
    override fun distanceAndDepth(distKm: Int, depthKm: Int) = "size $distKm km · $depthKm km derinlik"
    override fun timeAgo(seconds: Long): String {
        return when {
            seconds < 60 -> "$seconds sn önce"
            seconds < 3600 -> "${seconds / 60} dk önce"
            seconds < 86400 -> "${seconds / 3600} sa önce"
            else -> "${seconds / 86400} gün önce"
        }
    }
    override val sourceSolutions = "Kaynak çözümleri"
    override val backfill = "dolgu"
    override fun nearestCity(name: String, km: Int) = "En yakın büyük şehir: $name, $km km"
    override fun distanceToYou(km: Int, direction: String) = "Size uzaklığı: $km km ($direction yönünde)"
    override fun sourceRegionName(region: String) = "Kaynak bölge adı: $region"
    override fun firstReportedBy(source: String, min: Long) = "İlk haber: $source, oluştan $min dk sonra"
    override fun intensityEstimate(roman: String) = "Tahmini şiddet $roman — kaba tahmin, zemin etkisi hariç"
    override fun intensityLabel(mmi: Double): String = when {
        mmi < 2.0 -> "hissedilmez"
        mmi < 3.5 -> "zar zor hissedilir"
        mmi < 4.5 -> "hafif hissedilir"
        mmi < 5.5 -> "belirgin sallantı"
        mmi < 6.5 -> "güçlü sallantı"
        mmi < 7.5 -> "çok güçlü"
        else -> "şiddetli"
    }
    override fun compassBearing(deg: Double): String {
        val names = listOf("kuzeyinde", "kuzeydoğusunda", "doğusunda", "güneydoğusunda", "güneyinde", "güneybatısında", "batısında", "kuzeybatısında")
        return names[(((deg + 22.5) % 360) / 45).toInt()]
    }
    override fun magnitudeDiscrepancy(spread: String) = "Kaynaklar arası büyüklük farkı ±$spread"
    override val beforeMonitoringLatencyNotMeasured = "İzleme başlamadan önce olmuş — haber gecikmesi ölçülemedi"
    override fun estimatedShakingLine(intensityLabel: String, roman: String) = "Tahmini etki: $intensityLabel ($roman)"
    override val emscSourceHint = "WebSocket ile anlık + yedek yoklama."
    override val afadSourceHint = "Türkiye ulusal ağı, yoklamayla."
    override val koeriSourceHint = "Bağımsız üçüncü doğrulama."

    override val settingsTitle = "Ayarlar"
    override val languageSection = "Dil / Language"
    override val turkish = "Türkçe"
    override val english = "English"
    override val scopeSection = "Kapsam"
    override val monitoringRadius = "İzleme yarıçapı"
    override val notificationThreshold = "Bildirim eşiği"
    override val notificationThresholdHint = "Bu eşiğin altındaki depremler için telefon uyarmaz."
    override val listingThreshold = "Listeleme eşiği"
    override val allThreshold = "hepsi"
    override val listingThresholdHint = "Ekrandaki listeyi süzer, bildirimleri etkilemez. \"hepsi\" seçilirse yarıçap içindeki her deprem listelenir."
    override val locationSection = "Konum"
    override val useDeviceLocation = "Cihaz konumumu kullan"
    override val useDeviceLocationHint = "Açıkken referans noktası olarak cihazın son konumu alınır; kapalıyken sabit İstanbul merkezi kullanılır."
    override val sourcesSection = "Kaynaklar"
    override val sourcesSectionHint = "En az biri açık olmalı. EMSC canlı soket sunar, AFAD ve Kandilli 15–25 sn arayla yoklanır."
    override val emergencyAlarmSection = "Alarm"
    override val emergencyAlarmHint = "Bu eşiğin üstündeki yakın depremlerde telefon sessizde olsa da alarm sesiyle uyarır."
    override val alarmMagnitude = "Alarm büyüklüğü"
    override val alarmRadius = "Alarm yarıçapı"
    override val testAlarmButton = "Alarm sesini test et"
    override val testNotificationRegion = "TEST BİLDİRİMİ — gerçek deprem değil"

    override val serviceNotificationActive = "Deprem izleme aktif"
    override val serviceNotificationEmscLive = "Deprem izleme aktif · EMSC canlı"
    override val serviceNotificationOffline = "Deprem izleme · internet yok"
    override val serviceNotificationConnecting = "Kaynaklara bağlanılıyor…"
    override val quakeAlertTitle = "Deprem"
    override val quakeAlarmTitle = "YAKIN VE BÜYÜK DEPREM"
}

object EnStrings : AppStrings {
    override val appName = "Earthquake Tracker"
    override val monitoringActive = "Monitoring active"
    override val monitoringStopped = "Monitoring stopped"
    override val fixed = "fixed"
    override val deviceLocation = "device location"
    override val waitingLocation = "waiting for location"
    override val locationUnavailable = "location unavailable"
    override val referencePrefix = "Reference: "
    override val referenceIstanbul = "Istanbul"
    override fun locationStatusLabel(status: com.berk.deprem.core.LocationStatus) = when (status) {
        com.berk.deprem.core.LocationStatus.ARANIYOR -> "searching for location"
        com.berk.deprem.core.LocationStatus.IZIN_YOK -> "location permission not granted"
        com.berk.deprem.core.LocationStatus.SERVIS_KAPALI -> "location services disabled"
        com.berk.deprem.core.LocationStatus.BULUNAMADI -> "location unavailable"
    }
    override val kmRadius = "km radius"
    override val tapToRefresh = "Tap to refresh · Tap chips for details"
    override val settings = "Settings"

    override val live = "live"
    override val waiting = "waiting"
    override val noNetwork = "no network"
    override val error = "error"
    override fun secondsAgo(s: Long) = "${s}s"
    override fun firstReportsCount(count: Int) = "${count}× first"

    override fun sourceDialogTitle(source: Source) = "${source.short} (${source.name})"
    override val connectionLiveWs = "Connection: Live WebSocket connected"
    override val connectionNetworkError = "Connection: Network error (no internet)"
    override val connectionError = "Connection: Error occurred"
    override fun connectionHealthy(sec: Long) = "Connection: Healthy (${sec}s ago)"
    override val connectionWaitingFirst = "Connection: Waiting for initial poll..."
    override fun lastSuccessAgo(sec: Long) = "Last Successful Response: ${sec}s ago"
    override fun eventsObservedCount(count: Int) = "Events Observed: $count"
    override fun firstReportsDetail(count: Int) = "First Reported: $count times"
    override val errorDetails = "Error Details:"
    override val close = "Close"
    override val refreshNow = "Refresh Now"

    override val serversUnreachable = "Servers unreachable"
    override val noInternet = "No internet connection"
    override val dnsWarning = "Device appears connected but addresses cannot be resolved (DNS). Common after turning VPN on/off. Toggling Airplane mode or switching to Wi-Fi usually fixes it. Tap to retry."
    override val noInternetWarning = "Unable to query earthquake data sources. The issue is with the device connection. Monitoring will resume as soon as the connection is restored."
    override val batteryWarningTitle = "Battery optimization enabled"
    override val batteryWarningBody = "The system may put the monitoring service to sleep, delaying alerts. Tap to disable."
    override val noQuakesYet = "No earthquakes yet"
    override val emptyRunningSubtitle = "Earthquakes matching your radius and magnitude thresholds will appear here."
    override val emptyStoppedSubtitle = "Turn on the toggle switch above to start monitoring."
    override fun hiddenRecordsNotice(count: Int, mag: String) = "$count records hidden below threshold (M$mag)"

    override fun directionFrom(place: String, km: Int, direction: String): String {
        return "$km km $direction of $place"
    }
    override fun inCenterOf(place: String) = "Center of $place"
    override val unknownRegion = "Unknown region"
    override fun distanceAndDepth(distKm: Int, depthKm: Int) = "$distKm km away · $depthKm km depth"
    override fun timeAgo(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }
    override val sourceSolutions = "Source solutions"
    override val backfill = "backfill"
    override fun nearestCity(name: String, km: Int) = "Nearest major city: $name, $km km"
    override fun distanceToYou(km: Int, direction: String) = "Distance to you: $km km (heading $direction)"
    override fun sourceRegionName(region: String) = "Source region name: $region"
    override fun firstReportedBy(source: String, min: Long) = "First reported by: $source, $min min after origin"
    override fun intensityEstimate(roman: String) = "Estimated intensity $roman — rough estimate, ground effects excluded"
    override fun intensityLabel(mmi: Double): String = when {
        mmi < 2.0 -> "not felt"
        mmi < 3.5 -> "barely felt"
        mmi < 4.5 -> "weak shaking"
        mmi < 5.5 -> "moderate shaking"
        mmi < 6.5 -> "strong shaking"
        mmi < 7.5 -> "very strong"
        else -> "severe"
    }
    override fun compassBearing(deg: Double): String {
        val names = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
        return names[(((deg + 22.5) % 360) / 45).toInt()]
    }
    override fun magnitudeDiscrepancy(spread: String) = "Magnitude discrepancy between sources ±$spread"
    override val beforeMonitoringLatencyNotMeasured = "Occurred before monitoring started — latency not measured"
    override fun estimatedShakingLine(intensityLabel: String, roman: String) = "Estimated shaking: $intensityLabel ($roman)"
    override val emscSourceHint = "Instant WebSocket + backup polling."
    override val afadSourceHint = "Turkey national network, via polling."
    override val koeriSourceHint = "Independent third-party verification."

    override val settingsTitle = "Settings"
    override val languageSection = "Dil / Language"
    override val turkish = "Türkçe"
    override val english = "English"
    override val scopeSection = "Scope"
    override val monitoringRadius = "Monitoring radius"
    override val notificationThreshold = "Alert threshold"
    override val notificationThresholdHint = "Phone will not alert for earthquakes below this threshold."
    override val listingThreshold = "Listing threshold"
    override val allThreshold = "all"
    override val listingThresholdHint = "Filters the list on screen; does not affect alert notifications. \"all\" lists every earthquake within radius."
    override val locationSection = "Location"
    override val useDeviceLocation = "Use device location"
    override val useDeviceLocationHint = "When enabled, the reference point uses the device's location; otherwise, Istanbul center is used."
    override val sourcesSection = "Data Sources"
    override val sourcesSectionHint = "At least one must be enabled. EMSC provides a live WebSocket, while AFAD and Kandilli are polled every 15–25s."
    override val emergencyAlarmSection = "Emergency Alarm"
    override val emergencyAlarmHint = "Alarms sound loudly even if the phone is in silent/DND mode for strong nearby quakes."
    override val alarmMagnitude = "Alarm magnitude"
    override val alarmRadius = "Alarm radius"
    override val testAlarmButton = "Test alarm sound"
    override val testNotificationRegion = "TEST NOTIFICATION — Not a real earthquake"

    override val serviceNotificationActive = "Earthquake monitoring active"
    override val serviceNotificationEmscLive = "Earthquake monitoring active · EMSC live"
    override val serviceNotificationOffline = "Earthquake monitoring · No internet"
    override val serviceNotificationConnecting = "Connecting to sources…"
    override val quakeAlertTitle = "Earthquake"
    override val quakeAlarmTitle = "NEARBY STRONG EARTHQUAKE"
}

object Strings {
    fun get(lang: String): AppStrings = when (lang.lowercase()) {
        "en" -> EnStrings
        else -> TrStrings
    }
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { TrStrings }
