package com.homeremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

        private val icon192 by lazy { makeIconBytes(192) }
        private val icon512 by lazy { makeIconBytes(512) }

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/api/ping"      -> newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
                "/api/apps"      -> newFixedLengthResponse(
                    Response.Status.OK, "application/json",
                    gson.toJson(apps.getInstalledApps())
                )
                "/api/cmd"       -> handleCmd(session)
                "/manifest.json" -> serveManifest()
                "/sw.js"         -> serveSw()
                "/icon-192.png"  -> serveBytes(icon192, "image/png")
                "/icon-512.png"  -> serveBytes(icon512, "image/png")
                else -> try {
                    newChunkedResponse(Response.Status.OK, "text/html",
                        ctx.assets.open("remote/index.html"))
                } catch (_: IOException) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                }
            }
        }

        private fun serveManifest() = newFixedLengthResponse(
            Response.Status.OK, "application/manifest+json", """
            {
              "name": "HomeRemote",
              "short_name": "HomeRemote",
              "description": "Control your TV from your phone",
              "start_url": "/",
              "display": "standalone",
              "orientation": "portrait",
              "background_color": "#0f0f1a",
              "theme_color": "#533483",
              "icons": [
                {"src":"/icon-192.png","sizes":"192x192","type":"image/png","purpose":"any maskable"},
                {"src":"/icon-512.png","sizes":"512x512","type":"image/png","purpose":"any maskable"}
              ]
            }""".trimIndent()
        )

        private fun serveSw() = newFixedLengthResponse(
            Response.Status.OK, "application/javascript", """
            const CACHE = 'homeremote-v1';
            self.addEventListener('install', e => {
              e.waitUntil(caches.open(CACHE).then(c => c.add('/')));
              self.skipWaiting();
            });
            self.addEventListener('activate', e => { self.clients.claim(); });
            self.addEventListener('fetch', e => {
              if (new URL(e.request.url).pathname.startsWith('/api/')) return;
              e.respondWith(
                caches.match(e.request).then(cached => {
                  const fresh = fetch(e.request).then(r => {
                    caches.open(CACHE).then(c => c.put(e.request, r.clone()));
                    return r;
                  });
                  return cached || fresh;
                })
              );
            });""".trimIndent()
        )

        private fun serveBytes(bytes: ByteArray, mime: String) =
            newFixedLengthResponse(Response.Status.OK, mime,
                ByteArrayInputStream(bytes), bytes.size.toLong())

        private fun makeIconBytes(size: Int): ByteArray {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // Background
            p.color = 0xFF0f0f1a.toInt()
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)

            // Purple rounded card
            p.color = 0xFF533483.toInt()
            val m = size * 0.12f
            c.drawRoundRect(RectF(m, m, size - m, size - m), size * 0.18f, size * 0.18f, p)

            // Red dot (accent)
            p.color = 0xFFe94560.toInt()
            val dot = size * 0.09f
            c.drawCircle(size * 0.62f, size * 0.38f, dot, p)

            // "HR" label
            p.color = 0xFFFFFFFF.toInt()
            p.textSize = size * 0.30f
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            val fm = p.fontMetrics
            c.drawText("HR", size * 0.45f, size * 0.67f - (fm.ascent + fm.descent) / 2f, p)

            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            return out.toByteArray()
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
