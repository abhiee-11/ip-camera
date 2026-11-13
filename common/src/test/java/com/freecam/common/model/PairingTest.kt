package com.freecam.common.model

import com.freecam.common.model.Pairing
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PairingTest {

    @Test
    fun testJsonRoundTrip() {
        val original = Pairing(
            v = 1,
            name = "Test Camera",
            host = "100.64.0.5",
            rtspPort = 8554,
            httpPort = 8080,
            path = "live",
            token = "abcdef123456",
            mode = "local"
        )

        val json = original.toJson()
        val parsed = Pairing.fromJson(json)

        assertEquals(original.v, parsed.v)
        assertEquals(original.name, parsed.name)
        assertEquals(original.host, parsed.host)
        assertEquals(original.rtspPort, parsed.rtspPort)
        assertEquals(original.httpPort, parsed.httpPort)
        assertEquals(original.path, parsed.path)
        assertEquals(original.token, parsed.token)
        assertEquals(original.mode, parsed.mode)
    }

    @Test
    fun testDefaults() {
        val pairing = Pairing(
            name = "Minimal",
            host = "10.0.0.1",
            token = "token123"
        )

        assertEquals(1, pairing.v)
        assertEquals(8554, pairing.rtspPort)
        assertEquals(8080, pairing.httpPort)
        assertEquals("live", pairing.path)
        assertEquals("local", pairing.mode)
    }
}