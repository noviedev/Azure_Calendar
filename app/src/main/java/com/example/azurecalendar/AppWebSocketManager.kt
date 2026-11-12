package com.example.azurecalendar

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

class AppWebSocketManager(private val onStatusUpdated: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var currentUrl: String = "wss://socketsbay.com/wss/v2/1/demo/"
    private var isReconnectScheduled = false

    fun connect(serverUrl: String = "wss://socketsbay.com/wss/v2/1/demo/") {
        this.currentUrl = serverUrl
        if (webSocket != null) return

        try {
            val request = Request.Builder().url(serverUrl).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("WebSocket", "Connected to Realtime WebSocket Server successfully!")
                    isReconnectScheduled = false
                    webSocket.send("{\"type\":\"JOIN\", \"app\":\"AzureCalendar\", \"timestamp\":${System.currentTimeMillis()}}")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("WebSocket", "Received Realtime WebSocket Broadcast: $text")
                    mainHandler.post {
                        onStatusUpdated(text)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocket", "WebSocket connection closing: $reason")
                    webSocket.close(1000, null)
                    this@AppWebSocketManager.webSocket = null
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w("WebSocket", "WebSocket failure: ${t.message}. Retrying connection...")
                    this@AppWebSocketManager.webSocket = null
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            Log.e("WebSocket", "WebSocket connection error: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (isReconnectScheduled) return
        isReconnectScheduled = true
        mainHandler.postDelayed({
            isReconnectScheduled = false
            try {
                connect(currentUrl)
            } catch (_: Exception) {}
        }, 4000)
    }

    fun broadcastUpdate(message: String) {
        try {
            webSocket?.send(message)
        } catch (e: Exception) {
            Log.e("WebSocket", "Broadcast error: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Client disconnecting")
        } catch (_: Exception) {}
        webSocket = null
    }
}