package com.freecam.viewer.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream

/**
 * Loads camera thumbnails. The camera serves one JPEG per request; they are
 * small and are kept in memory, newest first.
 */
object ThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(16) {}
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun load(view: ImageView, url: String, token: String) {
        val key = "$url|$token"
        val cached = cache.get(key)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                // The placeholder stays. The camera cannot serve a frame yet.
            }

            override fun onResponse(call: Call, response: Response) {
                val bitmap = try {
                    response.body?.byteStream()?.use { decode(it) }
                } catch (e: Exception) {
                    null
                } finally {
                    response.close()
                }
                if (bitmap == null) return
                cache.put(key, bitmap)
                view.post { view.setImageBitmap(bitmap) }
            }
        })
    }

    private fun decode(stream: InputStream): Bitmap? {
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 200) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
