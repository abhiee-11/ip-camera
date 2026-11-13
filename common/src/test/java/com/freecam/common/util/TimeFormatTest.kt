package com.freecam.common.util

import com.freecam.common.util.TimeFormat
import org.junit.Assert.*
import org.junit.Test

class TimeFormatTest {

    @Test
    fun testFormatDuration() {
        assertEquals("00:00", TimeFormat.formatDuration(0))
        assertEquals("00:30", TimeFormat.formatDuration(30 * 1000))
        assertEquals("01:30", TimeFormat.formatDuration(90 * 1000))
        assertEquals("05:00", TimeFormat.formatDuration(5 * 60 * 1000))
        assertEquals("1:05:00", TimeFormat.formatDuration(65 * 60 * 1000))
        assertEquals("24:00:00", TimeFormat.formatDuration(24 * 60 * 60 * 1000))
    }

    @Test
    fun testFormatRelative() {
        val now = System.currentTimeMillis()
        assertEquals("0s ago", TimeFormat.formatRelative(now))
        assertEquals("30s ago", TimeFormat.formatRelative(now - 30_000))
        assertEquals("5m ago", TimeFormat.formatRelative(now - 5 * 60_000))
        assertEquals("2h ago", TimeFormat.formatRelative(now - 2 * 60 * 60_000))
        assertEquals("3d ago", TimeFormat.formatRelative(now - 3 * 24 * 60 * 60_000))
    }

    @Test
    fun testDayString() {
        val ts = 1704067200000L // 2024-01-01 00:00:00 UTC
        assertEquals("2024-01-01", TimeFormat.toDayString(ts))
    }

    @Test
    fun testParseDay() {
        val parsed = TimeFormat.parseDay("2024-01-15")
        assertNotNull(parsed)
        assertEquals("2024-01-15", TimeFormat.toDayString(parsed!!))
    }

    @Test
    fun testFileTimestamp() {
        val ts = 1704067200000L // 2024-01-01 00:00:00 UTC
        assertEquals("20240101_000000", TimeFormat.toFileTimestamp(ts))
    }
}