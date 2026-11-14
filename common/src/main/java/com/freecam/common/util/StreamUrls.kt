package com.freecam.common.util

/** Builders for the URLs the camera and viewer exchange. */
object StreamUrls {

    /** Live stream URL, carrying the RTSP credentials in the URI as Media3 expects. */
    fun buildRtspUrl(host: String, port: Int, path: String, user: String, token: String): String {
        val cleanPath = path.trimStart('/')
        return "rtsp://$user:$token@$host:$port/$cleanPath"
    }

    /** HTTP URL for the API or a recording file. The path keeps its leading slash. */
    fun buildHttpUrl(host: String, port: Int, path: String): String {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "http://$host:$port$cleanPath"
    }
}
