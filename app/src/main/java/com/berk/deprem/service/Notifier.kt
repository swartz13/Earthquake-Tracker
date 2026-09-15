package com.berk.deprem.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.berk.deprem.MainActivity
import com.berk.deprem.R
import com.berk.deprem.core.AlertLevel
import com.berk.deprem.core.Decision
import com.berk.deprem.core.Intensity
import com.berk.deprem.core.Places
import com.berk.deprem.model.Quake
import com.berk.deprem.model.Source
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

class Notifier(private val ctx: Context) {

    companion object {
        const val CH_ALARM = "quake_alarm"
        const val CH_INFO = "quake_info"
        const val CH_SERVICE = "monitor_service"
        private const val TAG = "Notifier"
        const val FG_NOTIFICATION_ID = 1001

        private val IST = ZoneId.of("Europe/Istanbul")
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    private val nm = NotificationManagerCompat.from(ctx)

    /**
     * Tek gonderim noktasi.
     *
     * Android 13+ POST_NOTIFICATIONS izni olmadan notify() sessizce duser.
     * Istisnayi yutmak yerine izni burada acikca kontrol ediyoruz: aksi halde
     * uygulama "calisiyor ama hic bildirim gelmiyor" durumuna dusuyor ve
     * sebebi hicbir yerde gorunmuyor.
     */
    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS izni yok — bildirim gonderilemiyor")
            return
        }
        runCatching { nm.notify(id, notification) }
            .onFailure { Log.w(TAG, "bildirim gonderilemedi: ${it.message}") }
    }

    fun ensureChannels() {
        val sys = ctx.getSystemService(NotificationManager::class.java)

        val alarm = NotificationChannel(CH_ALARM, "Deprem alarmi", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Esik ustu, yakin depremler. Sesli ve titresimli."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            // Alarm ses akisini kullaniyoruz: telefon sessizdeyken bile duyulmasi icin.
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        val info = NotificationChannel(CH_INFO, "Deprem bildirimi", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Esik ustu ama alarm seviyesinin altindaki depremler."
            enableVibration(true)
        }
        val service = NotificationChannel(CH_SERVICE, "Izleme servisi", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Arka planda izlemenin surdugunu gosteren kalici bildirim."
            setShowBadge(false)
        }
        sys.createNotificationChannels(listOf(alarm, info, service))
    }

    /** Ayni deprem icin hep ayni bildirim kimligi — guncellemeler ust uste biner. */
    private fun notifId(quake: Quake) = 2000 + (quake.id.hashCode() and 0x0FFFFFF)

    fun notifyQuake(quake: Quake, d: Decision) {
        val level = d.level
        if (level == AlertLevel.NONE) return

        val mag = String.format(Locale.getDefault(), "%.1f", quake.mag)
        val dist = d.distanceKm.roundToInt()
        val local = Instant.ofEpochMilli(quake.originTimeMs).atZone(IST).format(HHMM)

        val lang = com.berk.deprem.core.Repo.prefs.current.language
        val sStrings = com.berk.deprem.ui.Strings.get(lang)

        // Yerlesim tarifi ("Simav'ın 17 km kuzeyinde") EMSC'nin verdigi
        // "WESTERN TURKEY" gibi bolge kodlarindan cok daha bilgilendirici.
        val place = if (Places.loaded) Places.describe(quake.lat, quake.lon) else null
        val nerede = place?.localizedHeadline(sStrings) ?: quake.region.take(48)

        val prefix = if (level == AlertLevel.ALARM) "${sStrings.quakeAlarmTitle}  M$mag" else "${sStrings.quakeAlertTitle} M$mag"
        val title = "$prefix — $nerede"

        val text = if (lang == "en") "$dist km away · ${quake.depthKm.roundToInt()} km depth · $local"
        else "Size $dist km · ${quake.depthKm.roundToInt()} km derinlik · $local"

        val sourceLine = Source.entries.joinToString("  ") { s ->
            if (quake.reports.containsKey(s)) "${s.short} ✓" else "${s.short} –"
        }
        val mmiLine = sStrings.estimatedShakingLine(sStrings.intensityLabel(d.mmi), Intensity.roman(d.mmi))

        val spreadLine = if (quake.confirmCount > 1 && quake.magSpread >= 0.3) {
            "\n" + sStrings.magnitudeDiscrepancy(String.format(Locale.getDefault(), "%.1f", quake.magSpread))
        } else ""

        val cityLine = place?.city?.let {
            "\n" + sStrings.nearestCity(it.label, place.cityKm.roundToInt())
        }.orEmpty()

        val big = "$text$cityLine\n$sourceLine\n$mmiLine$spreadLine"

        val open = PendingIntent.getActivity(
            ctx, notifId(quake),
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("quakeId", quake.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = NotificationCompat.Builder(ctx, if (level == AlertLevel.ALARM) CH_ALARM else CH_INFO)
            .setSmallIcon(R.drawable.ic_quake)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(quake.originTimeMs)
            .setShowWhen(true)
            .setCategory(if (level == AlertLevel.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (level == AlertLevel.ALARM) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            // Sessiz guncellemede ses/titresim yok: dogrulama geldi diye telefon
            // ikinci kez calmasin.
            .setOnlyAlertOnce(!d.alert)
            .setSilent(!d.alert)

        if (level == AlertLevel.ALARM && d.alert) {
            b.setFullScreenIntent(open, true)
        }

        post(notifId(quake), b.build())
    }

    /** Servisi ayakta tutan kalici bildirim. */
    fun buildForeground(statusLine: String, detail: String) =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle(statusLine)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    fun updateForeground(statusLine: String, detail: String) {
        post(FG_NOTIFICATION_ID, buildForeground(statusLine, detail))
    }
}
