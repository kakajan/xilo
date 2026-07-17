package ir.xilo.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointRulesTest {

    @Test
    fun normalizeApiBaseUrl_addsTrailingSlash() {
        assertEquals("https://api.example.com/", EndpointRules.normalizeApiBaseUrl("https://api.example.com"))
    }

    @Test
    fun normalizeApiBaseUrl_keepsExistingSlash() {
        assertEquals("https://api.example.com/", EndpointRules.normalizeApiBaseUrl("https://api.example.com/"))
    }

    @Test
    fun isCleartextLocalhost_detectsEmulatorBridge() {
        assertTrue(EndpointRules.isCleartextLocalhost("http://10.0.2.2:8888/"))
        assertTrue(EndpointRules.isCleartextLocalhost("ws://10.0.2.2:8888/ws"))
        assertTrue(EndpointRules.isCleartextLocalhost("http://localhost:8888/"))
        assertTrue(EndpointRules.isCleartextLocalhost("http://127.0.0.1:8888/"))
    }

    @Test
    fun isCleartextLocalhost_allowsHttpsAndRemoteHttp() {
        assertFalse(EndpointRules.isCleartextLocalhost("https://api.example.com/"))
        assertFalse(EndpointRules.isCleartextLocalhost("wss://api.example.com/ws"))
        assertFalse(EndpointRules.isCleartextLocalhost("http://192.168.1.10:8888/"))
    }

    @Test
    fun redactedHeaders_includesAuthorization() {
        assertTrue(EndpointRules.redactedHeaders().contains("Authorization"))
    }
}
