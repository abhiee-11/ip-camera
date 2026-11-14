package com.freecam.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUrlsTest {

    @Test
    fun rtspUrlCarriesCredentialsAndPath() {
        assertEquals(
            "rtsp://freecam:abc123@100.64.0.5:8554/live",
            StreamUrls.buildRtspUrl("100.64.0.5", 8554, "live", "freecam", "abc123")
        )
    }

    @Test
    fun rtspUrlToleratesALeadingSlashOnThePath() {
        assertEquals(
            "rtsp://freecam:abc123@100.64.0.5:8554/live",
            StreamUrls.buildRtspUrl("100.64.0.5", 8554, "/live", "freecam", "abc123")
        )
    }

    @Test
    fun httpPlaybackUrlKeepsPathAndHost() {
        assertEquals(
            "http://100.64.0.5:8080/recordings/a.mp4",
            StreamUrls.buildHttpUrl("100.64.0.5", 8080, "/recordings/a.mp4")
        )
    }

    @Test
    fun httpUrlAddsTheLeadingSlashWhenMissing() {
        assertEquals(
            "http://100.64.0.5:8080/api/status",
            StreamUrls.buildHttpUrl("100.64.0.5", 8080, "api/status")
        )
    }
}
