package com.homeremote

import android.content.Context
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class RemoteServer(
    private val context: Context,
    private val appDiscovery: AppDiscovery,
    private val port: Int = 8080
) {
    private val gson = Gson()
    private var http: HttpServer? = null

    fun start() {
        http = HttpServer(context, appDiscovery, port, gson).also { it.start() }
    }

    fun stop() {
        try { http?.stop() } catch (_: Exception) {}
    }

    private class HttpServer(
        private val ctx: Context,
        private val apps: AppDiscovery,
        port: Int,
        private val gson: Gson
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/api/ping" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
                "/api/apps" -> newFixedLengthResponse(
                    Response.Status.OK, "application/json",
                    gson.toJson(apps.getInstalledApps())
                )
                "/api/cmd" -> handleCmd(session)
                else -> try {
                    newChunkedResponse(Response.Status.OK, "text/html",
                        ctx.assets.open("remote/index.html"))
                } catch (_: IOException) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                }
            }
        }

        private fun handleCmd(session: IHTTPSession): Response {
            return try {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)
                val json = files["postData"]
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No body")
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(json, Map::class.java) as Map<String, Any>
                val action = raw["action"]?.toString()
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing action")
                val value = raw["value"]?.toString() ?: ""
                CommandBus.post(Command(action, value))
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "error")
            }
        }
    }
}
