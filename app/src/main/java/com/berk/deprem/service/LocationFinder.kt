package com.berk.deprem.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Cihaz konumunu bulur.
 *
 * Farkli Android surumlerinde ve uretici ROM'larinda (OPPO, Xiaomi, Samsung vb.)
 * konum isteklerinin basarisiz olmamasi icin tum saglayicilari (FUSED, NETWORK, GPS, PASSIVE)
 * yedekli olarak sorgular ve taze olcum alinamasa dahi eldeki en son gecerli onbellegi kullanir.
 */
class LocationFinder(private val ctx: Context) {

    private companion object {
        const val TAG = "LocFinder"
        const val TAZE_ONBELLEK_MS = 5L * 60 * 1000 // 5 dakika
    }

    private val lm = ctx.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Once onbellekteki konumlara bakar; cok tazeyse hemen dondurur.
     * Degilse tum saglayicilardan yeni olcum ister. Yeni olcum gelmezse
     * elde olan son onbellegi kullanir.
     */
    suspend fun current(timeoutMs: Long = 15_000): Location? {
        if (!hasPermission()) {
            Log.w(TAG, "konum izni yok")
            return null
        }
        val lm = lm ?: return null
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            Log.w(TAG, "cihazda konum servisi kapali")
            return null
        }

        val onbellek = sonBilinen()
        if (onbellek != null && yas(onbellek) < TAZE_ONBELLEK_MS) {
            Log.i(TAG, "taze onbellek konumu kullanildi (${yas(onbellek) / 1000} sn once)")
            return onbellek
        }

        val taze = withTimeoutOrNull(timeoutMs) { yeniOlcum() }
        if (taze != null) {
            Log.i(TAG, "yeni olcum alindi: ${taze.latitude}, ${taze.longitude}")
            return taze
        }

        // Yeni olcum zaman asimina ugradiysa eldeki onbellek konumu
        // (ne kadar eski olursa olsun) deprem mesafesi hesaplamak icin hicten iyidir.
        if (onbellek != null) {
            Log.i(TAG, "yeni olcum gelmedi, onbellek konumu kullanildi (${yas(onbellek) / 1000} sn once)")
            return onbellek
        }

        Log.w(TAG, "hicbir konum bulunamadi")
        return null
    }

    private fun yas(l: Location) = System.currentTimeMillis() - l.time

    @SuppressLint("MissingPermission")
    private fun sonBilinen(): Location? {
        val lm = lm ?: return null
        val saglayicilar = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            addAll(runCatching { lm.getProviders(true) }.getOrDefault(emptyList()))
            addAll(runCatching { lm.allProviders }.getOrDefault(emptyList()))
        }.distinct()

        return saglayicilar
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .maxByOrNull { it.time }
    }

    /** Saglayicilardan eszamanli taze konum ister. */
    @SuppressLint("MissingPermission")
    private suspend fun yeniOlcum(): Location? = suspendCancellableCoroutine { cont ->
        val lm = lm
        if (lm == null) { cont.resume(null); return@suspendCancellableCoroutine }

        val activeProviders = buildList {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                add(LocationManager.FUSED_PROVIDER)
            }
        }.distinct()

        if (activeProviders.isEmpty()) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (location.latitude != 0.0 || location.longitude != 0.0) {
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }
            }
        }

        cont.invokeOnCancellation {
            runCatching { lm.removeUpdates(listener) }
        }

        var registeredAny = false
        for (provider in activeProviders) {
            runCatching {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                registeredAny = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val iptal = CancellationSignal()
            cont.invokeOnCancellation { runCatching { iptal.cancel() } }
            for (provider in activeProviders) {
                runCatching {
                    lm.getCurrentLocation(provider, iptal, ctx.mainExecutor) { loc ->
                        if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                            runCatching { lm.removeUpdates(listener) }
                            if (cont.isActive) cont.resume(loc)
                        }
                    }
                }
            }
        }

        if (!registeredAny && cont.isActive) {
            cont.resume(null)
        }
    }
}
