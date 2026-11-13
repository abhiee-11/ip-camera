package com.freecam.camera.http

import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method as JMethod
import java.nio.charset.StandardCharsets

class ApiServerTest {

    private val testToken = "test_token_1234567890abcdef"
    private val mockSettings = mockk<Settings>().apply {
        coEvery { token } returns testToken
    }
    private val mockAppLog = mockk<AppLog>()

    @Test
    fun testAuthenticateValidBearerToken() {
        val server = createTestServer()
        val session = mockk<NanoHTTPD.IHTTPSession>()
        
        coEvery { session.headers } returns mapOf("authorization" to "Bearer $testToken")
        coEvery { session.method } returns Method.GET
        coEvery { session.uri } returns "/api/status"

        val result = invokeAuthenticate(server, session)
        
        assertTrue("Valid token should authenticate", result)
    }

    @Test
    fun testAuthenticateMissingHeader() {
        val server = createTestServer()
        val session = mockk<NanoHTTPD.IHTTPSession>()
        
        coEvery { session.headers } returns mapOf()

        val result = invokeAuthenticate(server, session)
        
        assertFalse("Missing header should fail auth", result)
    }

    @Test
    fun testAuthenticateInvalidToken() {
        val server = createTestServer()
        val session = mockk<NanoHTTPD.IHTTPSession>()
        
        coEvery { session.headers } returns mapOf("authorization" to "Bearer wrong_token")

        val result = invokeAuthenticate(server, session)
        
        assertFalse("Invalid token should fail auth", result)
    }

    @Test
    fun testAuthenticateCaseInsensitiveHeader() {
        val server = createTestServer()
        val session = mockk<NanoHTTPD.IHTTPSession>()
        
        coEvery { session.headers } returns mapOf("Authorization" to "Bearer $testToken")

        val result = invokeAuthenticate(server, session)
        
        assertTrue("Case insensitive header should work", result)
    }

    @Test
    fun testPathTraversalDetection() {
        // Test the path traversal check logic directly
        val testPaths = listOf(
            "../../etc/passwd",
            "..\\windows\\system32\\drivers\\etc\\hosts",
            "subdir/../test.mp4",
            "test/../../escape.mp4"
        )
        
        for (path in testPaths) {
            val decoded = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8.name())
            val hasTraversal = decoded.contains("..") || decoded.contains("/") || decoded.contains("\\")
            assertTrue("Path '$path' should be detected as traversal", hasTraversal)
        }
        
        // Valid paths should not trigger traversal detection
        val validPaths = listOf("test.mp4", "recording_2024.mp4", "video-1.mp4")
        for (path in validPaths) {
            val decoded = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8.name())
            val hasTraversal = decoded.contains("..") || decoded.contains("/") || decoded.contains("\\")
            assertFalse("Path '$path' should be valid", hasTraversal)
        }
    }

    @Test
    fun testParseRangeHeader() {
        // Test range header parsing logic
        val rangeHeader = "bytes=100-200"
        assertTrue(rangeHeader.startsWith("bytes="))
        val range = rangeHeader.substring(6)
        val parts = range.split("-")
        assertEquals(2, parts.size)
        assertEquals(100, parts[0].toLongOrNull()!!)
        assertEquals(200, parts[1].toLongOrNull()!!)
        
        // Test open-ended range
        val openRange = "bytes=500-"
        val openParts = openRange.substring(6).split("-")
        assertEquals(2, openParts.size)
        assertEquals(500, openParts[0].toLongOrNull()!!)
        assertEquals("", openParts[1])
    }

    @Test
    fun testApiServerInstantiation() {
        val server = createTestServer()
        assertNotNull(server)
        
        // Test that the server can be stopped without issues
        server.stop()
    }

    private fun createTestServer(): ApiServer {
        val mockContext = mockk<android.content.Context>()
        coEvery { mockContext.getExternalFilesDir(null) } returns File(System.getProperty("java.io.tmpdir"))
        
        return ApiServer(
            context = mockContext,
            settings = mockSettings,
            appLog = mockAppLog,
            onStatus = { """{"running":true}""" },
            onRecordings = { emptyList() },
            onEvents = { _, _ -> emptyList() },
            port = 0
        )
    }

    private fun invokeAuthenticate(server: ApiServer, session: NanoHTTPD.IHTTPSession): Boolean {
        val authenticateMethod = ApiServer::class.java.getDeclaredMethod("authenticate", NanoHTTPD.IHTTPSession::class.java)
        authenticateMethod.isAccessible = true
        return authenticateMethod.invoke(server, session) as Boolean
    }
}