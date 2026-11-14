package com.freecam.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAddressTest {

    @Test
    fun tailnetRangeIsExactlyTheCgnatBlock() {
        assertTrue(HostAddress.isTailnetAddress("100.64.0.5"))
        assertTrue(HostAddress.isTailnetAddress("100.127.255.254"))
        assertFalse(HostAddress.isTailnetAddress("100.63.255.254"))
        assertFalse(HostAddress.isTailnetAddress("100.128.0.1"))
        assertFalse(HostAddress.isTailnetAddress("192.168.1.5"))
        assertFalse(HostAddress.isTailnetAddress("not-an-ip"))
    }

    @Test
    fun selectHostPrefersTailnetThenLan() {
        assertEquals(
            HostChoice("100.64.0.5", "100.64.0.5"),
            HostAddress.selectHost(listOf("192.168.1.20", "100.64.0.5"))
        )
        assertEquals(
            HostChoice("192.168.1.20", null),
            HostAddress.selectHost(listOf("192.168.1.20", "10.0.0.3"))
        )
        assertEquals(HostChoice(null, null), HostAddress.selectHost(listOf("127.0.0.1")))
        assertEquals(HostChoice(null, null), HostAddress.selectHost(emptyList()))
    }

    @Test
    fun loopbackIsNeverSelected() {
        assertNull(HostAddress.selectHost(listOf("127.0.0.1")).host)
    }
}
