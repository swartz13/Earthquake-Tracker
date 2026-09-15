package com.berk.deprem

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.berk.deprem.core.AlertLevel
import com.berk.deprem.core.Decision
import com.berk.deprem.core.Repo
import com.berk.deprem.model.Quake
import com.berk.deprem.model.Report
import com.berk.deprem.model.Source
import com.berk.deprem.service.Notifier
import com.berk.deprem.service.QuakeMonitorService
import com.berk.deprem.ui.DepremTheme
import com.berk.deprem.ui.QuakeScreen
import com.berk.deprem.ui.SettingsSheet
import androidx.compose.runtime.CompositionLocalProvider
import com.berk.deprem.ui.LocalStrings
import com.berk.deprem.ui.Strings
import com.berk.deprem.service.LocationFinder
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && Repo.prefs.current.monitorEnabled) QuakeMonitorService.start(this)
        }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { sonuc ->
            val verildi = sonuc.values.any { it }
            if (verildi) {
                // Izin yeni verildi: servisi yeniden baslatarak on plan tipine
                // konum yetkisini ekle, sonra hemen konum ara.
                QuakeMonitorService.start(this)
                Repo.requestLocationRefresh()
                refreshDeviceLocationNow()
            } else {
                Repo.prefs.update { it.copy(useDeviceLocation = false, homeFromDevice = false) }
            }
        }

    override fun onResume() {
        super.onResume()
        refreshDeviceLocationNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        lifecycleScope.launch { Repo.load() }

        requestNotificationPermission()
        if (Repo.prefs.current.monitorEnabled) QuakeMonitorService.start(this)

        setContent {
            val settings by Repo.prefs.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalStrings provides Strings.get(settings.language)) {
                DepremTheme {
                    val quakes by Repo.store.quakes.collectAsStateWithLifecycle()
                    val status by Repo.status.collectAsStateWithLifecycle()
                    val running by Repo.serviceRunning.collectAsStateWithLifecycle()
                    val placesReady by Repo.placesReady.collectAsStateWithLifecycle()
                    val online by Repo.online.collectAsStateWithLifecycle()
                    val locationStatus by Repo.locationStatus.collectAsStateWithLifecycle()
                    var showSettings by remember { mutableStateOf(false) }
                    var batteryOk by remember { mutableStateOf(isBatteryUnrestricted()) }

                    QuakeScreen(
                    quakes = quakes,
                    placesReady = placesReady,
                    status = status,
                    settings = settings,
                    serviceRunning = running,
                    online = online,
                    locationStatus = locationStatus,
                    batteryUnrestricted = batteryOk,
                    onToggleMonitor = { on ->
                        Repo.prefs.update { it.copy(monitorEnabled = on) }
                        if (on) {
                            requestNotificationPermission()
                            QuakeMonitorService.start(this)
                        } else {
                            QuakeMonitorService.stop(this)
                        }
                    },
                    onOpenSettings = { showSettings = true },
                    onFixBattery = {
                        requestBatteryExemption()
                        batteryOk = isBatteryUnrestricted()
                    },
                    onRefresh = {
                        // Yoklama donguleri bu sayaci izliyor; artirmak
                        // beklemeyi kesip hemen yeni istek attiriyor.
                        com.berk.deprem.data.Http.resetConnections()
                        Repo.bumpNetwork()
                        Repo.requestLocationRefresh()
                        refreshDeviceLocationNow()
                        if (!running && Repo.prefs.current.monitorEnabled) {
                            QuakeMonitorService.start(this)
                        }
                    },
                )

                if (showSettings) {
                    SettingsSheet(
                        settings = settings,
                        onChange = { block ->
                            val before = Repo.prefs.current
                            Repo.prefs.update(block)
                            val simdi = Repo.prefs.current
                            if (!before.useDeviceLocation && simdi.useDeviceLocation) {
                                if (hasLocationPermission()) {
                                    Repo.requestLocationRefresh()
                                    refreshDeviceLocationNow()
                                } else {
                                    locationPermission.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                        )
                                    )
                                }
                            }
                        },
                        onTestNotification = { sendTestNotification() },
                        onDismiss = { showSettings = false },
                    )
                }
            }
        }
    }
    }

    private fun refreshDeviceLocationNow() {
        if (!Repo.prefs.current.useDeviceLocation || !hasLocationPermission()) return
        lifecycleScope.launch {
            val finder = LocationFinder(this@MainActivity)
            val loc = finder.current(timeoutMs = 5000)
            if (loc != null) {
                Repo.prefs.update {
                    it.copy(homeLat = loc.latitude, homeLon = loc.longitude, homeFromDevice = true)
                }
                Repo.locationStatus.value = null
                Repo.bumpNetwork()
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isBatteryUnrestricted(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    /**
     * Pil optimizasyonu muafiyeti bu uygulamanin isleyisi icin kritik: muafiyet
     * olmadan sistem soketi kesip bildirimi dakikalarca geciktirebiliyor.
     */
    private fun requestBatteryExemption() {
        runCatching {
            startActivity(
                Intent(
                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            runCatching {
                startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /** Alarm kanalinin gercekten calistigini deprem beklemeden dogrulamak icin. */
    private fun sendTestNotification() {
        val s = Repo.prefs.current
        val now = System.currentTimeMillis()
        val fake = Quake(
            id = "test-$now",
            reports = mapOf(
                Source.AFAD to Report(
                    source = Source.AFAD,
                    sourceEventId = "test",
                    originTimeMs = now - 30_000,
                    lat = s.homeLat + 0.35,
                    lon = s.homeLon + 0.25,
                    depthKm = 8.0,
                    mag = s.alarmMag,
                    magType = "ML",
                    region = "TEST BILDIRIMI — gercek deprem degil",
                    receivedAtMs = now,
                )
            ),
            firstSeenAtMs = now,
            lastUpdatedMs = now,
        )
        Notifier(this).notifyQuake(
            fake,
            Decision(AlertLevel.ALARM, distanceKm = 42.0, mmi = 5.0, alert = true)
        )
    }
}
