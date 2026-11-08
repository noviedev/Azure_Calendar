package com.example.azurecalendar

import android.util.Log
import okhttp3.*

class AppWebSocketManager(private val onStatusUpdated: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect(serverUrl: String) {
        val request = Request.Builder().url(serverUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to WebSocket server successfully!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received real-time update: $text")
                // Pass incoming status change back to UI / database helper
                onStatusUpdated(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Connection closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Connection failed: {t.message}")
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }
}