package com.berk.deprem.data

import android.util.Log
import com.berk.deprem.model.Report
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * EMSC "standing order" WebSocket'i.
 *
 * Uygulamanin en hizli kanali: EMSC bir cozum yayinladigi anda mesaj dusuyor,
 * yoklama araligini beklemek gerekmiyor. Baglanti koptugunda ustel geri cekilmeyle
 * yeniden baglanir; bu sirada olusan bosluklari [EmscSource] yoklamasi kapatir.
 */
class EmscWebSocket(
    private val onReport: suspend (Report) -> Unit,
) {
    companion object {
        private const val TAG = "EmscWS"
        private const val URL = "wss://www.seismicportal.eu/standing_order/websocket"
        private const val MAX_BACKOFF_MS = 60_000L
    }

    /** UI'da "EMSC canli" rozetini yakan bayrak. */
    val connected = MutableStateFlow(false)
    val lastMessageAtMs = MutableStateFlow(0L)

    @Volatile private var socket: WebSocket? = null

    /**
     * @param networkGeneration her ag degisiminde artan sayac. Geri cekilme
     *   beklemesi bu degisimle kesilir; wifi'ye gecince ya da VPN kapaninca
     *   60 saniyelik beklemenin dolmasini beklemeden yeniden baglanir.
     */
    fun start(scope: CoroutineScope, networkGeneration: StateFlow<Int>? = null) = scope.launch {
        emitScope = scope
        var backoff = 2_000L
        while (isActive) {
            val cleanClose = runCatching { connectAndWait() }.getOrElse { e ->
                Log.w(TAG, "baglanti hatasi: ${e.message}")
                false
            }
            connected.value = false
            if (!isActive) break
            // Duzgun kapanmada hemen, hatada gittikce artan araliklarla dene.
            backoff = if (cleanClose) 2_000L else (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)

            if (networkGeneration == null) {
                delay(backoff)
            } else {
                val kusak = networkGeneration.value
                val agDegisti = withTimeoutOrNull(backoff) {
                    networkGeneration.first { it != kusak }
                } != null
                if (agDegisti) {
                    Log.i(TAG, "ag degisti, hemen yeniden baglaniliyor")
                    backoff = 2_000L
                }
            }
        }
        close()
    }

    /** Soket kapanana kadar askida kalir. Donus: kapanis temiz miydi. */
    private suspend fun connectAndWait(): Boolean = suspendCancellableCoroutine { cont ->
        val req = Request.Builder().url(URL).header("User-Agent", Http.UA).build()
        val ws = Http.client.newWebSocket(req, listener(cont))
        socket = ws
        cont.invokeOnCancellation { runCatching { ws.close(1000, "iptal") } }
    }

    private fun listener(cont: CancellableContinuation<Boolean>) = object : WebSocketListener() {
        /** Devamlilik yalnizca bir kez tamamlanabilir; cift resume'u burada engelliyoruz. */
        private var finished = false

        private fun finish(clean: Boolean) {
            if (finished) return
            finished = true
            if (cont.isActive) cont.resume(clean)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "baglandi")
            connected.value = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            lastMessageAtMs.value = System.currentTimeMillis()
            handle(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            connected.value = false
            finish(code == 1000)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected.value = false
            finish(code == 1000)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "soket dustu: ${t.message}")
            connected.value = false
            finish(false)
        }
    }

    /** Mesaj semasi: {"action":"create"|"update","data":{ ...GeoJSON feature... }} */
    private fun handle(text: String) {
        val report = runCatching {
            val root = JSONObject(text)
            val action = root.optString("action", "create")
            if (action == "delete") return@runCatching null
            val data = root.optJSONObject("data") ?: return@runCatching null
            parseEmscFeature(data)
        }.getOrElse {
            Log.w(TAG, "mesaj ayristirilamadi: ${it.message}")
            null
        } ?: return

        emitScope?.launch { onReport(report) }
    }

    @Volatile private var emitScope: CoroutineScope? = null

    fun close() {
        runCatching { socket?.close(1000, "kapatildi") }
        socket = null
        connected.value = false
    }
}
