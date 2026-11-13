package com.freecam.common.util

import com.freecam.common.util.Bytes
import org.junit.Assert.*
import org.junit.Test

class BytesTest {

    @Test
    fun testFormat() {
        assertEquals("512 B", Bytes.format(512))
        assertEquals("1.0 KB", Bytes.format(1024))
        assertEquals("1.5 KB", Bytes.format(1536))
        assertEquals("1.0 MB", Bytes.format(1024 * 1024))
        assertEquals("1.5 MB", Bytes.format(1536 * 1024))
        assertEquals("1.0 GB", Bytes.format(1024 * 1024 * 1024))
        assertEquals("1.5 GB", Bytes.format(1536 * 1024 * 1024))
    }

    @Test
    fun testParse() {
        assertEquals(1024L, Bytes.parseKb(1))
        assertEquals(1024 * 1024L, Bytes.parseMb(1))
        assertEquals(1024 * 1024 * 1024L, Bytes.parseGb(1))
        assertEquals(5 * 1024 * 1024 * 1024L, Bytes.parseGb(5))
    }
}