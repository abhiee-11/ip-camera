package com.freecam.common

import com.freecam.common.model.Pairing
import com.freecam.common.util.UrlBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairingTest {
    @Test
    fun testPairingJsonRoundtrip() {
        val original = Pairing(
            name = "Camera 1",
            host = "100.64.1.2",
            token = "abcdef1234567890"
        )
        val json = original.toJson()
        val parsed = Pairing.fromJson(json)
        assertThat(parsed.name).isEqualTo(original.name)
        assertThat(parsed.host).isEqualTo(original.host)
        assertThat(parsed.token).isEqualTo(original.token)
        assertThat(parsed.rtspPort).isEqualTo(8554)
        assertThat(parsed.httpPort).isEqualTo(8080)
        assertThat(parsed.path).isEqualTo("live")
        assertThat(parsed.mode).isEqualTo("local")
    }

    @Test
    fun testPairingJsonWithCustomValues() {
        val original = Pairing(
            v = 1,
            name = "Backyard",
            host = "192.168.1.50",
            rtspPort = 554,
            httpPort = 8080,
            path = "stream",
            token = "customtoken123",
            mode = "relay"
        )
        val json = original.toJson()
        val parsed = Pairing.fromJson(json)
        assertThat(parsed.v).isEqualTo(1)
        assertThat(parsed.name).isEqualTo("Backyard")
        assertThat(parsed.host).isEqualTo("192.168.1.50")
        assertThat(parsed.rtspPort).isEqualTo(554)
        assertThat(parsed.httpPort).isEqualTo(8080)
        assertThat(parsed.path).isEqualTo("stream")
        assertThat(parsed.token).isEqualTo("customtoken123")
        assertThat(parsed.mode).isEqualTo("relay")
    }
}

class UrlBuilderTest {
    @Test
    fun testBuildRtspUrl() {
        val url = UrlBuilder.buildRtspUrl("100.64.1.2", 8554, "live")
        assertThat(url).isEqualTo("rtsp://100.64.1.2:8554/live")
    }

    @Test
    fun testBuildHttpUrl() {
        val url = UrlBuilder.buildHttpUrl("100.64.1.2", 8080, "/api/status")
        assertThat(url).isEqualTo("http://100.64.1.2:8080/api/status")
    }

    @Test
    fun testBuildClipUrl() {
        val url = UrlBuilder.buildClipUrl("100.64.1.2", 8080, "20240101_120000.mp4")
        assertThat(url).isEqualTo("http://100.64.1.2:8080/recordings/20240101_120000.mp4")
    }

    @Test
    fun testBuildThumbUrl() {
        val url = UrlBuilder.buildThumbUrl("100.64.1.2", 8080, "20240101_120000.jpg")
        assertThat(url).isEqualTo("http://100.64.1.2:8080/thumbs/20240101_120000.jpg")
    }
}