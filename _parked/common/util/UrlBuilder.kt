package com.freecam.common.util

import com.freecam.common.api.ApiPaths

object UrlBuilder {
    fun buildRtspUrl(host: String, port: Int, path: String): String {
        return "rtsp://$host:$port/$path"
    }

    fun buildHttpUrl(host: String, port: Int, path: String): String {
        return "http://$host:$port$path"
    }

    fun buildClipUrl(host: String, port: Int, fileName: String): String {
        return buildHttpUrl(host, port, "${ApiPaths.RECORDINGS_FILE}$fileName")
    }

    fun buildThumbUrl(host: String, port: Int, fileName: String): String {
        return buildHttpUrl(host, port, "${ApiPaths.THUMBS_FILE}$fileName")
    }
}