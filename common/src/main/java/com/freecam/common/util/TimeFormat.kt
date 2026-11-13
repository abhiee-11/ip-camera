package com.freecam.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    fun toIsoString(ts: Long): String = isoFormat.format(Date(ts))
    fun toDayString(ts: Long): String = dayFormat.format(Date(ts))
    fun toFileTimestamp(ts: Long): String = fileFormat.format(Date(ts))
    fun toDisplayString(ts: Long): String = displayFormat.format(Date(ts))
    fun parseDay(day: String): Long? = try { dayFormat.parse(day)?.time } catch (e: Exception) { null }
    fun now(): Long = System.currentTimeMillis()
}