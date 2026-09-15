package com.berk.deprem.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.berk.deprem.core.Repo
import com.berk.deprem.data.Http

/**
 * Varsayilan agi izler ve her degisimde izleme donguleri hemen yeniden dener.
 *
 * Bu olmadan: wifi'ye gecince ya da VPN acilip kapaninca eldeki baglantilar
 * olu kaliyor, istekler hata veriyor ve ustel geri cekilme devreye girdigi
 * icin uygulama dakikalarca sessiz kaliyordu. Kullanicinin gordugu sey
 * "uc kaynak da hata" oluyordu.
 */
class NetworkWatcher(context: Context) {

    private companion object { const val TAG = "NetWatch" }

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            Log.i(TAG, "ag geldi: $network")
            yenidenBasla("ag geldi")
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "ag gitti: $network")
            Repo.online.value = false
            // Olu soketleri hemen at; yeni ag gelince temiz baslayalim.
            Http.resetConnections()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hazir = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val oncekiDurum = Repo.online.value
            Repo.online.value = hazir
            // Yalnizca "yok"tan "var"a geciste zorla; her yetenek degisiminde degil.
            if (hazir && !oncekiDurum) yenidenBasla("internet dogrulandi")
        }
    }

    private fun yenidenBasla(sebep: String) {
        Repo.online.value = true
        Http.resetConnections()
        Repo.bumpNetwork()
        Log.i(TAG, "yoklamalar yeniden tetiklendi ($sebep)")
    }

    fun register() {
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            // Baslangic durumunu hemen yansit.
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            Repo.online.value = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.onFailure { Log.w(TAG, "ag izleyici kaydedilemedi: ${it.message}") }
    }

    fun unregister() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}
