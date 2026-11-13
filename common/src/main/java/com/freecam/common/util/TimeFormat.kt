package com.freecam.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object TimeFormat {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val displayFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
    private val fullFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US)

    fun toIsoString(ts: Long): String = isoFormat.format(Date(ts))
    fun toDayString(ts: Long): String = dayFormat.format(Date(ts))
    fun toFileTimestamp(ts: Long): String = fileFormat.format(Date(ts))
    fun toDisplayString(ts: Long): String = displayFormat.format(Date(ts))
    fun formatFull(ts: Long): String = fullFormat.format(Date(ts))
    fun parseDay(day: String): Long? = try { dayFormat.parse(day)?.time } catch (e: Exception) { null }
    fun now(): Long = System.currentTimeMillis()

    fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    fun formatRelative(ts: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - ts
        return when {
            diff < 60_000 -> "${diff / 1000}s ago"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    }
}