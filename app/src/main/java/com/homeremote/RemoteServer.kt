package com.homeremote

import android.content.Context
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.IOException
import java.net.InetSocketAddress

class RemoteServer(
    private val context: Context,
    private val appDiscovery: AppDiscovery,
    private val httpPort: Int = 8080
) {
    private val wsPort = httpPort + 1
    private val gson = Gson()
    private var http: HttpServer? = null
    private var ws: WsServer? = null

    fun start() {
        http = HttpServer(context, appDiscovery, httpPort, gson).also { it.start() }
        ws = WsServer(wsPort, gson).also { it.start() }
    }

    fun stop() {
        try { http?.stop() } catch (_: Exception) {}
        try { ws?.stop(0) } catch (_: Exception) {}
    }

    private class HttpServer(
        private val ctx: Context,
        private val apps: AppDiscovery,
        port: Int,
        private val gson: Gson
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/api/apps" -> newFixedLengthResponse(
                    Response.Status.OK, "application/json", gson.toJson(apps.getInstalledApps())
                )
                else -> try {
                    newChunkedResponse(Response.Status.OK, "text/html",
                        ctx.assets.open("remote/index.html"))
                } catch (e: IOException) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                }
            }
        }
    }

    private class WsServer(port: Int, private val gson: Gson) :
        WebSocketServer(InetSocketAddress(port)) {

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {}
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {}
        override fun onError(conn: WebSocket?, ex: Exception) {}
        override fun onStart() {}

        override fun onMessage(conn: WebSocket, message: String) {
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(message, Map::class.java) as Map<String, Any>
                val action = raw["action"]?.toString() ?: return
                val value = raw["value"]?.toString() ?: ""
                CommandBus.post(Command(action, value))
            } catch (_: Exception) {}
        }
    }
}
