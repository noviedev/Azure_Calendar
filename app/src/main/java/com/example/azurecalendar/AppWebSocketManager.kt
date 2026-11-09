package com.example.azurecalendar

import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

class AppWebSocketManager(private val onStatusUpdated: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var currentUrl: String = "wss://echo.websocket.events"

    fun connect(serverUrl: String = "wss://echo.websocket.events") {
        this.currentUrl = serverUrl
        if (webSocket != null) return

        try {
            val request = Request.Builder().url(serverUrl).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("WebSocket", "Connected to Realtime WebSocket Server successfully!")
                    webSocket.send("{\"type\":\"JOIN\", \"app\":\"AzureCalendar\"}")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("WebSocket", "Received Realtime WebSocket Broadcast: $text")
                    onStatusUpdated(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocket", "WebSocket connection closing: $reason")
                    webSocket.close(1000, null)
                    this@AppWebSocketManager.webSocket = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w("WebSocket", "WebSocket connection message/failure: ${t.message}")
                    this@AppWebSocketManager.webSocket = null
                }
            })
        } catch (e: Exception) {
            Log.e("WebSocket", "WebSocket connection error: ${e.message}")
        }
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